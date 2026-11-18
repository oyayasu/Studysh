mod installer;
use installer::{Installation, Reporter};
use command_group::GroupChild;
use fs2::FileExt;
use tauri::ipc::Channel;
use std::sync::{Arc, atomic::{AtomicBool, Ordering}};
use serde::Serialize;
use serde_json::{json, Value};
use std::fs;
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{ChildStdin, ChildStdout, Command, Stdio};
use std::sync::Mutex;
use tauri::{path::BaseDirectory, Manager};

const BRIDGE: &str = include_str!("../python/qwen_tts_bridge.py");
const MODEL: &str = "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice";
const PYTHON_ENV_VERSION: &str = "qwen3-tts-managed-python-3.12-v2";

#[tauri::command]
fn open_json_file() -> Result<Option<String>, String> {
    let Some(path) = rfd::FileDialog::new()
        .add_filter("JSON", &["json"])
        .pick_file() else { return Ok(None) };
    fs::read_to_string(path).map(Some).map_err(|e| e.to_string())
}

#[tauri::command]
fn save_json_file(content: String) -> Result<Option<String>, String> {
    let Some(path) = rfd::FileDialog::new()
        .add_filter("JSON", &["json"])
        .set_file_name("studysh-backup.json")
        .save_file() else { return Ok(None) };
    fs::write(&path, content).map_err(|e| e.to_string())?;
    Ok(Some(path.to_string_lossy().into_owned()))
}

#[derive(Serialize)]
struct QwenStatus {
    dependencies_installed: bool,
    model_downloaded: bool,
    model_name: &'static str,
    device: &'static str,
}

fn qwen_root(app: &tauri::AppHandle) -> Result<PathBuf, String> {
    app.path()
        .app_local_data_dir()
        .map(|dir| dir.join("qwen3-tts"))
        .map_err(|e| e.to_string())
}

fn venv_python(root: &Path) -> PathBuf {
    if cfg!(windows) {
        root.join("venv").join("Scripts").join("python.exe")
    } else {
        root.join("venv").join("bin").join("python")
    }
}

fn script_path(root: &Path) -> PathBuf {
    root.join("qwen_tts_bridge.py")
}

fn model_path(root: &Path) -> PathBuf {
    root.join("model")
}

fn write_bridge(root: &Path) -> Result<PathBuf, String> {
    fs::create_dir_all(root).map_err(|e| e.to_string())?;
    let path = script_path(root);
    fs::write(&path, BRIDGE).map_err(|e| e.to_string())?;
    Ok(path)
}

#[tauri::command]
fn qwen_status(app: tauri::AppHandle) -> Result<QwenStatus, String> {
    let root = qwen_root(&app)?;
    let python = venv_python(&root);
    let installed = python.is_file() && environment_ready(&root);
    let model = installed && verified(&root);
    Ok(QwenStatus {
        dependencies_installed: installed,
        model_downloaded: model,
        model_name: MODEL,
        device: "CPU",
    })
}

// Studysh bundles the signed, SHA256-verified Astral uv executable.
// uv downloads a CPython 3.12 interpreter exclusively into Studysh's local
// app data directory; it never needs or modifies the user's Python or PATH.
fn environment_ready(root: &Path) -> bool {
    fs::read_to_string(root.join(".dependencies-ready"))
        .is_ok_and(|value| value == PYTHON_ENV_VERSION)
        && venv_python(root).is_file()
}

fn verified(root: &Path) -> bool {
    fs::read_to_string(model_path(root).join(".studysh-verified")).is_ok_and(|s| s == MODEL)
}

fn uv_command(app: &tauri::AppHandle, root: &Path) -> Result<Command, String> {
    let binary = app.path()
        .resolve("resources/uv.exe", BaseDirectory::Resource)
        .map_err(|e| e.to_string())?;
    if !binary.is_file() {
        return Err("The Studysh installer is missing its bundled Python manager (uv.exe). Reinstall the latest Windows build.".into());
    }
    let mut cmd = Command::new(binary);
    cmd.env("UV_PYTHON_INSTALL_DIR", root.join("python"))
        .env("UV_PYTHON_BIN_DIR", root.join("bin"))
        .env("UV_CACHE_DIR", root.join("uv-cache"))
        .env("UV_NO_MODIFY_PATH", "1")
        .env("UV_NO_PROGRESS", "1")
        // --managed-python on `uv venv` is the single source of truth. A
        // UV_PYTHON_PREFERENCE override conflicts with that flag in uv 0.13.
        .env_remove("UV_PYTHON_PREFERENCE");
    Ok(cmd)
}

fn setup(app: &tauri::AppHandle, channel: Channel<Value>, cancel: Arc<AtomicBool>) -> Result<(), String> {
    let root = qwen_root(app)?;
    fs::create_dir_all(&root).map_err(|e| e.to_string())?;
    let lock = fs::OpenOptions::new().create(true).write(true).open(root.join("setup.lock"))
        .map_err(|e| e.to_string())?;
    lock.try_lock_exclusive().map_err(|_| "Another Studysh installation is running.")?;
    let log = fs::File::create(root.join("install.log")).map_err(|e| e.to_string())?;
    let report = Reporter { cancel, channel, log: Mutex::new(log) };
    let result = install(app, &root, &report);
    if let Err(ref error) = result { report.line(error); }
    result
}

fn install(app: &tauri::AppHandle, root: &Path, report: &Reporter) -> Result<(), String> {
    report.stage("1/4 · Preparing private Python 3.12")?;
    let python = venv_python(root);
    let venv = root.join("venv");
    let cfg = fs::read_to_string(venv.join("pyvenv.cfg")).unwrap_or_default();
    let managed = cfg.lines().filter_map(|line| line.split_once(" = "))
        .any(|(key, path)| key == "home" && Path::new(path).starts_with(root.join("python")));
    let compatible = cfg.lines().any(|line| line.starts_with("version_info = 3.12.")
        || line.starts_with("version = 3.12."));
    if !python.is_file() || !managed || !compatible {
        let mut create = uv_command(app, root)?;
        create.args(["venv", "--python", "3.12", "--managed-python", "--clear"]).arg(&venv);
        report.run(&mut create, "Creating the app-managed environment", false)?;
        if root.join(".dependencies-ready").exists() {
            fs::remove_file(root.join(".dependencies-ready")).map_err(|e| e.to_string())?;
        }
    } else { report.line("Reusing private Python 3.12."); }

    report.stage("2/4 · Installing speech dependencies")?;
    if !environment_ready(root) {
        let mut torch = uv_command(app, root)?;
        torch.args(["pip", "install", "--python"]).arg(&python)
            .args(["torch==2.8.0", "torchaudio==2.8.0", "--index-url", "https://download.pytorch.org/whl/cpu"]);
        report.run(&mut torch, "Installing CPU PyTorch and torchaudio", false)?;
        let mut qwen = uv_command(app, root)?;
        qwen.args(["pip", "install", "--python"]).arg(&python)
            .args(["qwen-tts==0.1.1", "soundfile"]);
        report.run(&mut qwen, "Installing Qwen3-TTS packages", false)?;
        fs::write(root.join(".dependencies-ready"), PYTHON_ENV_VERSION).map_err(|e| e.to_string())?;
    } else { report.line("Reusing installed dependencies."); }

    report.stage("3/4 · Downloading Qwen3-TTS model and tokenizer")?;
    let script = write_bridge(root)?;
    let bridge = |mode: &str| {
        let mut cmd = Command::new(&python);
        cmd.arg("-u").arg(&script).arg(mode).arg("--model-dir").arg(model_path(root))
            .env("HF_HOME", root.join("huggingface"));
        cmd
    };
    report.run(&mut bridge("--prepare"), "Checking model files and resuming downloads", true)?;
    report.stage("4/4 · Verifying offline model and Python libraries")?;
    if let Err(e) = report.run(&mut bridge("--verify"), "Loading the voice model on CPU", false) {
        // Let uv reconcile packages on retry, preserving its wheels and the model.
        if !report.cancel.load(Ordering::Acquire) { let _ = fs::remove_file(root.join(".dependencies-ready")); }
        return Err(e);
    }
    fs::write(model_path(root).join(".studysh-ready"), MODEL).map_err(|e| e.to_string())?;
    fs::write(model_path(root).join(".studysh-verified"), MODEL).map_err(|e| e.to_string())?;
    report.line("Offline verification passed.");
    Ok(())
}

#[tauri::command]
async fn qwen_install(app: tauri::AppHandle, updates: Channel<Value>) -> Result<(), String> {
    let cancel = Arc::new(AtomicBool::new(false));
    {
        let state = app.state::<Installation>();
        let mut active = state.0.lock().map_err(|e| e.to_string())?;
        if active.is_some() { return Err("Installation already running.".into()); }
        *active = Some(cancel.clone());
    }
    let worker = app.clone();
    let result = tauri::async_runtime::spawn_blocking(move || setup(&worker, updates, cancel)).await;
    *app.state::<Installation>().0.lock().map_err(|e| e.to_string())? = None;
    result.map_err(|e| e.to_string())?
}

#[tauri::command]
fn qwen_cancel(app: tauri::AppHandle) -> Result<(), String> {
    if let Some(cancel) = app.state::<Installation>().0.lock().map_err(|e| e.to_string())?.as_ref() {
        cancel.store(true, Ordering::Release);
    }
    Ok(())
}

#[tauri::command]
fn qwen_log(app: tauri::AppHandle) -> Result<String, String> {
    use std::io::{Read, Seek, SeekFrom};
    let path = qwen_root(&app)?.join("install.log");
    if !path.exists() { return Ok(String::new()); }
    let mut f = fs::File::open(path).map_err(|e| e.to_string())?;
    let start = f.metadata().map_err(|e| e.to_string())?.len().saturating_sub(64 * 1024);
    f.seek(SeekFrom::Start(start)).map_err(|e| e.to_string())?;
    let mut tail = Vec::new();
    f.read_to_end(&mut tail).map_err(|e| e.to_string())?;
    Ok(String::from_utf8_lossy(&tail).into_owned())
}

struct QwenProcess {
    child: GroupChild,
    stdin: ChildStdin,
    stdout: BufReader<ChildStdout>,
}
impl Drop for QwenProcess {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.inner().wait();
    }
}
impl QwenProcess {
    fn response(&mut self) -> Result<Value, String> {
        let mut line = String::new();
        if self.stdout.read_line(&mut line).map_err(|e| e.to_string())? == 0 {
            return Err("Qwen3 speech worker stopped unexpectedly.".into());
        }
        let value: Value = serde_json::from_str(&line)
            .map_err(|e| format!("Invalid Qwen3 worker response: {e}"))?;
        if let Some(error) = value.get("error").and_then(Value::as_str) {
            return Err(error.to_string());
        }
        Ok(value)
    }
    fn speak(&mut self, text: &str, language: &str, speaker: &str) -> Result<String, String> {
        let request = json!({"text": text, "language": language, "speaker": speaker});
        writeln!(self.stdin, "{request}").map_err(|e| e.to_string())?;
        self.stdin.flush().map_err(|e| e.to_string())?;
        self.response()?
            .get("audio_base64")
            .and_then(Value::as_str)
            .map(String::from)
            .ok_or_else(|| "Qwen3 did not generate an audio file.".into())
    }
}

#[derive(Default)]
struct QwenRuntime(Mutex<Option<QwenProcess>>);

fn start_qwen(app: &tauri::AppHandle) -> Result<QwenProcess, String> {
    let root = qwen_root(app)?;
    if !environment_ready(&root)
        || !model_path(&root).join(".studysh-ready").is_file()
    {
        return Err("Qwen3 is not installed. Open Settings → Voice and download it first.".into());
    }
    let script = write_bridge(&root)?;
    let mut command = Command::new(venv_python(&root));
    command
        .arg("-u")
        .arg(script)
        .arg("--serve")
        .arg("--model-dir")
        .arg(model_path(&root))
        .env("HF_HOME", root.join("huggingface"))
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(fs::File::create(root.join("speech.log")).map_err(|e| e.to_string())?);
    let mut child = installer::spawn(&mut command).map_err(|e| e.to_string())?;
    let stdin = child.inner().stdin.take().ok_or("Qwen3 worker has no stdin")?;
    let stdout = child.inner().stdout.take().ok_or("Qwen3 worker has no stdout")?;
    let mut process = QwenProcess { child, stdin, stdout: BufReader::new(stdout) };
    if process.response()?.get("ready").and_then(Value::as_bool) != Some(true) {
        return Err("Qwen3 worker did not become ready.".into());
    }
    Ok(process)
}

fn synthesize(
    app: &tauri::AppHandle,
    text: String,
    language: String,
    speaker: String,
) -> Result<String, String> {
    if text.trim().is_empty() || text.chars().count() > 1200 {
        return Err("Voice text must be between 1 and 1200 characters.".into());
    }
    if !["English", "Japanese", "Spanish", "Portuguese"].contains(&language.as_str()) {
        return Err("Unsupported Studysh language.".into());
    }
    if !["Vivian", "Serena", "Uncle_Fu", "Dylan", "Eric", "Ryan", "Aiden", "Ono_Anna", "Sohee"]
        .contains(&speaker.as_str())
    {
        return Err("Unsupported Qwen3 speaker.".into());
    }
    let runtime = app.state::<QwenRuntime>();
    let mut process = runtime.0.lock().map_err(|e| e.to_string())?;
    if process.is_none() {
        *process = Some(start_qwen(app)?);
    }
    let result = process.as_mut().ok_or("Qwen3 worker unavailable")?
        .speak(&text, &language, &speaker);
    if result.is_err() {
        *process = None; // A failed worker will be restarted on the next request.
    }
    result
}

#[tauri::command]
async fn qwen_synthesize(
    app: tauri::AppHandle,
    text: String,
    language: String,
    speaker: String,
) -> Result<String, String> {
    tauri::async_runtime::spawn_blocking(move || synthesize(&app, text, language, speaker))
        .await
        .map_err(|e| e.to_string())?
}

pub fn run() {
    tauri::Builder::default()
        .manage(QwenRuntime::default())
        .manage(Installation::default())
        .invoke_handler(tauri::generate_handler![
            open_json_file, save_json_file, qwen_status, qwen_install,
            qwen_cancel, qwen_log, qwen_synthesize
        ])
        .run(tauri::generate_context!())
        .expect("error while running Studysh");
}
