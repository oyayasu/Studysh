use serde::Serialize;
use serde_json::{json, Value};
use std::fs;
use std::io::{BufRead, BufReader, Write};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdin, ChildStdout, Command, Stdio};
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
    let model = installed && model_path(&root).join(".studysh-ready").is_file();
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
        .env("UV_PYTHON_PREFERENCE", "only-managed");
    Ok(cmd)
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
    if environment_ready(&root) {
        return Ok(());
    }

    // A partially installed or previously incompatible environment is safe
    // to recreate: voice models and downloaded uv cache are in other folders.
    let venv = root.join("venv");
    if venv.exists() {
        fs::remove_dir_all(&venv).map_err(|e| {
            format!("Cannot replace old Qwen3 Python environment: {e}. Close other Studysh processes and retry.")
        })?;
    }
    let mut create = uv_command(app, &root)?;
    create.args(["venv", "--python", "3.12", "--managed-python"])
        .arg(&venv);
    checked_command(create, "Creating private Python 3.12 environment")?;

    let python = venv_python(&root);
    if !python.is_file() {
        return Err("The managed Python 3.12 environment was not created correctly.".into());
    }
    // Use CPU-specific PyTorch and torchaudio as one coherent pair rather
    // than pulling CUDA packages on Windows with an AMD GPU.
    let mut torch = uv_command(app, &root)?;
    torch.arg("pip").arg("install").arg("--python").arg(&python)
        .args(["torch", "torchaudio", "--index-url", "https://download.pytorch.org/whl/cpu"]);
    checked_command(torch, "Installing CPU PyTorch and torchaudio")?;

    let mut qwen = uv_command(app, &root)?;
    qwen.arg("pip").arg("install").arg("--python").arg(&python)
        .args(["qwen-tts==0.1.1", "soundfile", "huggingface_hub"]);
    checked_command(qwen, "Installing Qwen3-TTS packages")?;

    let mut test = Command::new(&python);
    test.args(["-c", "import torch, torchaudio, qwen_tts, soundfile; print(torch.__version__)"]);
    checked_command(test, "Validating Qwen3-TTS Python libraries")?;

    fs::write(root.join(".dependencies-ready"), PYTHON_ENV_VERSION)
        .map_err(|e| e.to_string())?;
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
    if !environment_ready(&root) {
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
    if !environment_ready(&root)
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
