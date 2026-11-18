package com.oyayasu.studysh

data class StudyQuery(val term:String="",val context:String="",val sentence:String="",val doubt:String="")
data class Note(val title:String,val type:String,val content:String,val examples:List<Pair<String,String>> = emptyList())
data class Entry(val term:String,val pronunciation:String,val status:String,val notes:List<Note>,val original:String,val partOfSpeech:String="",val language:String="English"){ val id:String get()=StudyLanguage.lessonKey(language,term) }
