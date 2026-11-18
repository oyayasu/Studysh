package com.oyayasu.studysh

import org.json.JSONArray
import org.json.JSONObject

internal fun parseEntries(raw:String):List<Entry>{
 val root=JSONObject(raw);val arr=root.optJSONArray("entries")?:JSONArray()
 return (0 until arr.length()).mapNotNull { i ->
  val obj=arr.optJSONObject(i)?:return@mapNotNull null
  val blocks=obj.optJSONArray("blocks")?:obj.optJSONArray("notes")?:JSONArray()
  val notes=(0 until blocks.length()).mapNotNull { j ->
   blocks.optJSONObject(j)?.let { block ->
    val examples=block.optJSONArray("examples")?:JSONArray()
    Note(block.optString("title","Explanation"),block.optString("style",block.optString("type","purple")),
     block.optString("text",block.optString("content","")),
     (0 until examples.length()).mapNotNull { k -> examples.optJSONObject(k)?.let{ex -> Pair(ex.optString("en"),ex.optString("pt"))} })
   }
  }
  Entry(obj.optString("term"),obj.optString("pronunciation"),obj.optString("status",""),notes,obj.toString(),obj.optString("partOfSpeech"),StudyLanguage.canonical(obj.optString("language","English")))
 }.filter { it.term.isNotBlank() }
}

object StudyLibrary {
 fun merge(entries:List<Entry>, incoming:List<Entry>):List<JSONObject>{
   val map=linkedMapOf<String,JSONObject>()
   entries.forEach { map[it.id]=JSONObject(it.original) }
   incoming.forEach { item ->
    val key=item.id
    val old=map[key]
    val replacement=JSONObject(item.original)
    if(old!=null){
     val personal=old.optJSONArray("personalNotes")
     if(personal!=null)replacement.put("personalNotes",personal)
     val oldNotes=old.optJSONArray("notes")
     if(oldNotes!=null){
      val saved=JSONArray()
      for(j in 0 until oldNotes.length()){
       val n=oldNotes.optJSONObject(j)?:continue
       if(n.optString("type")=="custom")saved.put(n)
      }
      if(saved.length()>0){
       val existing=replacement.optJSONArray("personalNotes")?:JSONArray()
       for(j in 0 until saved.length())existing.put(saved.getJSONObject(j))
       replacement.put("personalNotes",existing)
      }
     }
    }
    map[key]=replacement
   }
   return map.values.toList()

 }
}
