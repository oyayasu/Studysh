use command_group::{CommandGroup, GroupChild};
use std::{
    fs::File,
    io::{self, BufRead, BufReader, Read, Write},
    process::{Command, Stdio},
    sync::{atomic::{AtomicBool, Ordering}, Arc, Mutex},
    thread,
    time::Duration,
};
use serde_json::{json, Value};
use tauri::ipc::Channel;

pub fn spawn(command: &mut Command) -> io::Result<GroupChild> {
    command.env("PYTHONUTF8", "1").env("PYTHONUNBUFFERED", "1");
    let mut group = command.group();
    #[cfg(windows)]
    group.creation_flags(0x08000000).kill_on_drop(true); // CREATE_NO_WINDOW, job owns descendants
    group.spawn()
}

#[derive(Default)]
pub struct Installation(pub Mutex<Option<Arc<AtomicBool>>>);

pub struct Reporter {
    pub cancel: Arc<AtomicBool>,
    pub channel: Channel<Value>,
    pub log: Mutex<File>,
}

impl Reporter {
    fn check(&self) -> Result<(), String> {
        if self.cancel.load(Ordering::Acquire) {
            Err("Installation cancelled. Downloaded files were kept.".into())
        } else {
            Ok(())
        }
    }

    pub fn line(&self, line: &str) {
        if let Ok(mut file) = self.log.lock() {
            let _ = writeln!(file, "{line}");
        }
        let _ = self.channel.send(json!({"line": line}));
    }

    pub fn stage(&self, label: &str) -> Result<(), String> {
        self.check()?;
        self.line(label);
        let _ = self.channel.send(json!({"stage": label}));
        Ok(())
    }

    fn read(&self, stream: impl Read, progress: bool) -> io::Result<()> {
        for bytes in BufReader::new(stream).split(b'\n') {
            let bytes = bytes?;
            let line = String::from_utf8_lossy(&bytes);
            if progress {
                if let Ok(data) = serde_json::from_str::<Value>(&line) {
                    if data["done"].is_u64() && data["total"].is_u64() {
                        let _ = self.channel.send(data);
                        continue;
                    }
                }
            }
            self.line(line.trim_end());
        }
        Ok(())
    }

    pub fn run(&self, command: &mut Command, label: &str, progress: bool) -> Result<(), String> {
        self.check()?;
        self.line(label);
        command.stdin(Stdio::null()).stdout(Stdio::piped()).stderr(Stdio::piped());
        let mut child = spawn(command).map_err(|e| format!("{label}: {e}"))?;
        let stdout = child.inner().stdout.take().unwrap();
        let stderr = child.inner().stderr.take().unwrap();
        thread::scope(|s| -> Result<(), String> {
            let out = s.spawn(|| self.read(stdout, progress));
            let err = s.spawn(|| self.read(stderr, false));
            let status = loop {
                if self.cancel.load(Ordering::Acquire) {
                    let _ = child.kill();
                    break child.inner().wait();
                }
                match child.inner().try_wait() {
                    Ok(Some(status)) => break Ok(status),
                    Ok(None) => thread::sleep(Duration::from_millis(100)),
                    Err(error) => { let _ = child.kill(); break Err(error); }
                }
            };
            for reader in [out, err] {
                reader.join().map_err(|_| "Log reader stopped")?.map_err(|e| e.to_string())?;
            }
            self.check()?;
            let status = status.map_err(|e| format!("{label}: {e}"))?;
            if status.success() { Ok(()) } else { Err(format!("{label} failed ({status}). See installation details.")) }
        })
    }
}
