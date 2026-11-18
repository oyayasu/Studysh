use std::fs;

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

pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![open_json_file, save_json_file])
        .run(tauri::generate_context!())
        .expect("error while running Studysh");
}
