package com.oyayasu.studysh

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.*

/**
 * Studysh FSRS-6 scheduling engine, default 21 parameters and 90% desired retention.
 * Reference: open-spaced-repetition/py-fsrs v6; FSRS-6 DSR equations.
 * This is the scheduler, not the parameter optimizer.
 */
internal object StudyFsrs {
 private val w=doubleArrayOf(
  0.212,1.2931,2.3065,8.2956,6.4133,0.8334,3.0194,0.001,1.8722,
  0.1666,0.796,1.4835,0.0614,0.2629,1.6483,0.6014,1.8729,
  0.5425,0.0912,0.0658,0.1542
 )
 private const val DAY=86_400_000L
 private const val RETENTION=0.9
 private val decay=-w[20]
 private val factor=RETENTION.pow(1.0/decay)-1.0
 private fun initialDifficulty(g:Int)=(w[4]-exp(w[5]*(g-1))+1).coerceIn(1.0,10.0)
 private fun difficulty(d:Double,g:Int):Double{
  val delta=-w[6]*(g-3)*(10-d)/9
  return (w[7]*initialDifficulty(4)+(1-w[7])*(d+delta)).coerceIn(1.0,10.0)
 }
 private fun recall(t:Double,s:Double)=(1+factor*t/s).pow(decay).coerceIn(0.0,1.0)
 private fun interval(stability:Double):Long {
  val days=(stability/factor*(RETENTION.pow(1/decay)-1)).roundToLong().coerceIn(1,36500)
  return days*DAY
 }
 private fun fresh(g:Int)=w[g-1].coerceIn(0.1,36500.0) to initialDifficulty(g)
 private fun updated(s:Double,d:Double,t:Double,g:Int):Pair<Double,Double>{
  val nextD=difficulty(d,g)
  val r=recall(t,s)
  val nextS=when {
   t<1 -> {
    val candidate=s*exp(w[17]*(g-3+w[18]))*s.pow(-w[19])
    if(g>=2)max(s,candidate) else candidate
   }
   g==1 -> min(s,w[11]*nextD.pow(-w[12])*((s+1).pow(w[13])-1)*exp((1-r)*w[14]))
   else -> {
    val multiplier=exp(w[8])*(11-nextD)*s.pow(-w[9])*(exp((1-r)*w[10])-1)*
     (if(g==2)w[15] else 1.0)*(if(g==4)w[16] else 1.0)
    s*(1+multiplier)
   }
  }
  return nextS.coerceIn(0.001,36500.0) to nextD
 }
 /**
  * Rating: 1 Again, 2 Hard, 3 Good, 4 Easy.
  * Learning steps 1m/10m; relearning 10m.
  * Persists algorithm state and an append-only review log.
  */
 fun review(previous:JSONObject?,rating:Int,now:Long=System.currentTimeMillis()):JSONObject {
  require(rating in 1..4)
  val card=if(previous==null)JSONObject() else JSONObject(previous.toString())
  val state=card.optString("state","new")
  val last=card.optLong("lastAt",0L)
  val elapsed=if(last<=0)0.0 else ((now-last).coerceAtLeast(0L).toDouble()/DAY)
  val oldS=card.optDouble("stability",0.0)
  val oldD=card.optDouble("difficulty",0.0)
  val hasMemory=oldS>0.0 && oldD in 1.0..10.0
  var nextState="review"
  var step=0
  var stability=oldS
  var diff=oldD
  var due=now+DAY
  val isLearning=state=="learning" || state=="relearning"
  val wasRelearning=state=="relearning"
  if(state=="new" || (!hasMemory && !isLearning)){
   val initial=fresh(rating)
   stability=initial.first;diff=initial.second
   when(rating){
    1->{nextState="learning";step=0;due=now+60_000L}
    2->{nextState="learning";step=0;due=now+600_000L}
    3->{nextState="learning";step=1;due=now+600_000L}
    4->due=now+interval(stability)
   }
  }else if(isLearning){
   // Each learning attempt is a memory observation, including same-day repetitions.
   // A lapse enters relearning; subsequent short steps do not repeatedly count a new lapse.
   val revised=if(hasMemory)updated(oldS,oldD,elapsed,rating) else fresh(rating)
   stability=revised.first;diff=revised.second
   val oldStep=card.optInt("step",0)
   when(rating){
    1->{nextState=if(wasRelearning)"relearning" else "learning";step=0;due=now+60_000L}
    2->{nextState=if(wasRelearning)"relearning" else "learning";step=oldStep;due=now+600_000L}
    3->if(oldStep==0 && !wasRelearning){
     nextState="learning";step=1;due=now+600_000L
    }else due=now+interval(stability)
    4->due=now+interval(stability)
   }
  }else{
   val revised=updated(oldS,oldD,elapsed,rating)
   stability=revised.first;diff=revised.second
   if(rating==1){nextState="relearning";step=0;due=now+600_000L}
   else due=now+interval(stability)
  }
  // Safeguard against invalid clocks or invalid persisted values.
  if(!stability.isFinite() || stability<=0.0 || !diff.isFinite()){
   val fallback=fresh(rating)
   stability=fallback.first;diff=fallback.second
   due=now+interval(stability);nextState="review";step=0
  }
  val logs=card.optJSONArray("reviews")?:JSONArray()
  logs.put(JSONObject().put("at",now).put("rating",rating).put("elapsedDays",elapsed)
   .put("stability",stability).put("difficulty",diff).put("dueAt",due)
   .put("state",nextState))
  card.put("algorithm","fsrs-6").put("state",nextState).put("step",step)
   .put("stability",stability).put("difficulty",diff).put("lastAt",now)
   .put("dueAt",due).put("reviews",logs)
   .put("attempts",card.optInt("attempts",0)+1)
   .put("correct",card.optInt("correct",0)+(if(rating==1)0 else 1))
  return card
 }
 /** Converts old fixed-interval cards conservatively without inventing FSRS reviews. */
 fun migrateLegacy(previous:JSONObject?,now:Long=System.currentTimeMillis()):JSONObject?{
  if(previous==null)return null
  if(previous.optString("algorithm")=="fsrs-6")return previous
  val attempts=previous.optInt("attempts",0)
  if(attempts<=0)return previous
  val streak=previous.optInt("streak",0)
  val grade=if(streak==0)1 else if(streak>=3)4 else 3
  val initial=fresh(grade)
  val migrated=JSONObject(previous.toString())
  migrated.put("algorithm","fsrs-6").put("state",if(streak==0)"relearning" else "review")
   .put("step",0).put("stability",initial.first).put("difficulty",initial.second)
  val oldDue=previous.optLong("dueAt",now)
  migrated.put("dueAt",oldDue.coerceAtLeast(0L))
  migrated.put("reviews",previous.optJSONArray("reviews")?:JSONArray())
  migrated.put("migration","legacy-estimate")
  return migrated
 }
 fun dueAt(card:JSONObject?):Long=card?.optLong("dueAt",0L)?:0L
 fun retention(card:JSONObject?,now:Long=System.currentTimeMillis()):Double? {
  if(card==null || card.optString("state")!="review")return null
  val stability=card.optDouble("stability",0.0)
  if(stability<=0)return null
  val elapsed=(now-card.optLong("lastAt",now)).coerceAtLeast(0L).toDouble()/DAY
  return recall(elapsed,stability)
 }
}
