use serde::Serialize;
use serde_json::{json, Value};
use std::fs;
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, ChildStdout, Command, Stdio};
use std::sync::Mutex;
use tauri::Manager;

const BRIDGE: &str = include_str!("../python/qwen_tts_bridge.py");
const MODEL: &str = "Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice";

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
    let installed = python.is_file() && root.join(".dependencies-ready").is_file();
    let model = installed && model_path(&root).join(".studysh-ready").is_file();
    Ok(QwenStatus {
        dependencies_installed: installed,
        model_downloaded: model,
        model_name: MODEL,
        device: "CPU",
    })
}

fn compatible_python() -> Result<(String, Vec<String>), String> {
    for (program, prefix) in [
        ("py", vec!["-3.12"]),
        ("py", vec!["-3.11"]),
        ("python", vec![]),
    ] {
        let mut cmd = Command::new(program);
        cmd.args(&prefix)
            .args(["-c", "import sys; assert (3, 11) <= sys.version_info[:2] <= (3, 12)"]);
        if cmd.stdout(Stdio::null()).stderr(Stdio::null()).status().is_ok_and(|s| s.success()) {
            return Ok((program.to_string(), prefix.into_iter().map(String::from).collect()));
        }
    }
    Err("Install Python 3.11 or 3.12 for Windows from python.org, then retry Qwen3 setup.".into())
}

fn checked_command(mut command: Command, stage: &str) -> Result<(), String> {
    let output = command.output().map_err(|e| format!("{stage}: {e}"))?;
    if output.status.success() {
        Ok(())
    } else {
        let error = String::from_utf8_lossy(&output.stderr);
        let detail: String = error.chars().rev().take(1800).collect::<String>().chars().rev().collect();
        Err(format!("{stage} failed: {detail}"))
    }
}

fn install_dependencies(app: &tauri::AppHandle) -> Result<(), String> {
    let root = qwen_root(app)?;
    fs::create_dir_all(&root).map_err(|e| e.to_string())?;
    if root.join(".dependencies-ready").is_file() && venv_python(&root).is_file() {
        return Ok(());
    }
    let python = venv_python(&root);
    if !python.is_file() {
        let (program, prefix) = compatible_python()?;
        let mut cmd = Command::new(program);
        cmd.args(prefix).args(["-m", "venv"]).arg(root.join("venv"));
        checked_command(cmd, "Creating Qwen3 Python environment")?;
    }
    let mut pip = Command::new(&python);
    pip.args(["-m", "pip", "install", "--disable-pip-version-check", "--upgrade", "pip"]);
    checked_command(pip, "Updating pip")?;

    // The AMD RX 7600 does not support CUDA; choose a genuine CPU Torch wheel
    // instead of downloading NVIDIA dependencies or silently claiming GPU speed.
    let mut torch = Command::new(&python);
    torch.args([
        "-m", "pip", "install", "--disable-pip-version-check",
        "torch", "--index-url", "https://download.pytorch.org/whl/cpu",
    ]);
    checked_command(torch, "Installing CPU PyTorch")?;

    let mut qwen = Command::new(&python);
    qwen.args([
        "-m", "pip", "install", "--disable-pip-version-check",
        "qwen-tts", "soundfile", "huggingface_hub",
    ]);
    checked_command(qwen, "Installing Qwen3-TTS packages")?;
    fs::write(root.join(".dependencies-ready"), MODEL).map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
async fn qwen_install(app: tauri::AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || install_dependencies(&app))
        .await
        .map_err(|e| e.to_string())?
}

fn download_model(app: &tauri::AppHandle) -> Result<(), String> {
    let root = qwen_root(app)?;
    if !root.join(".dependencies-ready").is_file() {
        return Err("Install Qwen3 dependencies first.".into());
    }
    if model_path(&root).join(".studysh-ready").is_file() {
        return Ok(());
    }
    let script = write_bridge(&root)?;
    let mut cmd = Command::new(venv_python(&root));
    cmd.arg(script)
        .arg("--prepare")
        .arg("--model-dir")
        .arg(model_path(&root))
        .env("HF_HOME", root.join("huggingface"));
    checked_command(cmd, "Downloading Qwen3 0.6B voice model")
}

#[tauri::command]
async fn qwen_prepare(app: tauri::AppHandle) -> Result<(), String> {
    tauri::async_runtime::spawn_blocking(move || download_model(&app))
        .await
        .map_err(|e| e.to_string())?
}

struct QwenProcess {
    child: Child,
    stdin: ChildStdin,
    stdout: BufReader<ChildStdout>,
}
impl Drop for QwenProcess {
    fn drop(&mut self) {
        let _ = self.child.kill();
        let _ = self.child.wait();
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
    if !root.join(".dependencies-ready").is_file()
        || !model_path(&root).join(".studysh-ready").is_file()
    {
        return Err("Qwen3 is not installed. Open Settings → Voice and download it first.".into());
    }
    let script = write_bridge(&root)?;
    let mut child = Command::new(venv_python(&root))
        .arg("-u")
        .arg(script)
        .arg("--serve")
        .arg("--model-dir")
        .arg(model_path(&root))
        .env("HF_HOME", root.join("huggingface"))
        .stdin(Stdio::piped())
        .stdout(Stdio::piped())
        .stderr(Stdio::null())
        .spawn()
        .map_err(|e| e.to_string())?;
    let stdin = child.stdin.take().ok_or("Qwen3 worker has no stdin")?;
    let stdout = child.stdout.take().ok_or("Qwen3 worker has no stdout")?;
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
        .invoke_handler(tauri::generate_handler![
            open_json_file, save_json_file, qwen_status, qwen_install,
            qwen_prepare, qwen_synthesize
        ])
        .run(tauri::generate_context!())
        .expect("error while running Studysh");
}
