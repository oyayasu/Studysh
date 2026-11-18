package com.oyayasu.studysh

import androidx.compose.runtime.*

/** Display language is independent of the language being studied. */
object StudyshI18n {
 var language by mutableStateOf("en")
 private val strings:Map<String,List<String>> = mapOf(
  "Settings" to listOf("Configurações","Configuración","設定"),
  "App language" to listOf("Idioma do aplicativo","Idioma de la aplicación","アプリの言語"),
  "Language to study" to listOf("Idioma de estudo","Idioma de estudio","学習する言語"),
  "Choose the language used by new study prompts. Existing lessons remain unchanged." to listOf("Escolha o idioma dos novos prompts. As lições existentes não serão alteradas.","Elige el idioma de los nuevos prompts. Las lecciones existentes no cambiarán.","新しい学習プロンプトの言語を選択します。既存のレッスンは変更されません。"),
  "Lessons layout" to listOf("Layout das lições","Diseño de lecciones","レッスンの表示"),
  "Compact layout" to listOf("Layout compacto","Diseño compacto","コンパクト表示"),
  "Voice settings" to listOf("Configurações de voz","Configuración de voz","音声設定"),
  "Home" to listOf("Início","Inicio","ホーム"),
  "Create" to listOf("Criar","Crear","作成"),
  "Lessons" to listOf("Lições","Lecciones","レッスン"),
  "Practice" to listOf("Prática","Práctica","練習"),
  "Voice" to listOf("Voz","Voz","音声"),
  "Sound effects" to listOf("Efeitos sonoros","Efectos de sonido","効果音"),
  "Quiet cartoon sounds during practice" to listOf("Sons discretos de cartoon durante a prática","Sonidos discretos de cartoon durante la práctica","練習中の控えめでかわいい効果音"),
  "Welcome to Studysh" to listOf("Boas-vindas ao Studysh","Bienvenido a Studysh","Studyshへようこそ"),
  "New search" to listOf("Nova pesquisa","Nueva búsqueda","新しい検索"),
  "Add term" to listOf("Adicionar termo","Añadir término","単語を追加"),
  "Capture from book" to listOf("Capturar do livro","Capturar del libro","本から取り込む"),
  "Copy study prompt" to listOf("Copiar prompt de estudo","Copiar prompt de estudio","学習プロンプトをコピー"),
  "Import AI response" to listOf("Importar resposta da IA","Importar respuesta de IA","AIの回答を取り込む"),
  "Your vocabulary library" to listOf("Sua biblioteca de vocabulário","Tu biblioteca de vocabulario","単語ライブラリ"),
  "Search your terms" to listOf("Pesquisar termos","Buscar términos","単語を検索"),
  "All" to listOf("Todos","Todos","すべて"),
  "Other" to listOf("Outros","Otros","その他"),
  "My notes" to listOf("Minhas notas","Mis notas","自分のメモ"),
  "Save note" to listOf("Salvar nota","Guardar nota","メモを保存"),
  "Edit note" to listOf("Editar nota","Editar nota","メモを編集"),
  "Delete note" to listOf("Excluir nota","Eliminar nota","メモを削除"),
  "Save changes" to listOf("Salvar alterações","Guardar cambios","変更を保存"),
  "Delete" to listOf("Excluir","Eliminar","削除"),
  "Cancel" to listOf("Cancelar","Cancelar","キャンセル"),
  "Back" to listOf("Voltar","Volver","戻る"),
  "Selected" to listOf("Selecionado","Seleccionado","選択中"),
  "Download" to listOf("Baixar","Descargar","ダウンロード"),
  "Preview" to listOf("Ouvir","Escuchar","試聴"),
  "English" to listOf("Inglês","Inglés","英語"),
  "Spanish" to listOf("Espanhol","Español","スペイン語"),
  "Japanese" to listOf("Japonês","Japonés","日本語"),
  "Portuguese" to listOf("Português","Portugués","ポルトガル語"),
  "Studying" to listOf("Estudando","Estudiando","学習中"),
  "Your learning space" to listOf("Seu espaço de aprendizagem","Tu espacio de aprendizaje","学習スペース"),
  "Add your own observation..." to listOf("Adicione sua observação...","Añade tu observación...","自分のメモを追加..."),
  "Edit term" to listOf("Editar termo","Editar término","単語を編集"),
  "Delete term" to listOf("Excluir termo","Eliminar término","単語を削除"),
  "Check" to listOf("Verificar","Comprobar","確認"),
  "Continue" to listOf("Continuar","Continuar","続ける"),
  "Start practice" to listOf("Iniciar prática","Iniciar práctica","練習を開始"),
  "No lessons yet" to listOf("Nenhuma lição ainda","Todavía no hay lecciones","レッスンはまだありません"),
  "Your vocabulary" to listOf("Seu vocabulário","Tu vocabulario","単語帳")
 )
 fun text(key:String):String {
  val index=when(language){"pt"->0;"es"->1;"ja"->2;else->return key}
  return strings[key]?.getOrNull(index)?:key
 }
}
fun ui(key:String):String=StudyshI18n.text(key)
