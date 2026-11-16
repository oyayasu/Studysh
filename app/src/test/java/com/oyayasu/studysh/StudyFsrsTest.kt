package com.oyayasu.studysh

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudyFsrsTest {
 private val day=86_400_000L
 private val start=1_700_000_000_000L

 @Test fun firstFailureSchedulesLearningStep(){
  val card=StudyFsrs.review(null,1,start)
  assertEquals("learning",card.getString("state"))
  assertEquals(start+60_000L,card.getLong("dueAt"))
  assertEquals(1,card.getJSONArray("reviews").length())
 }

 @Test fun twoGoodLearningStepsGraduate(){
  val first=StudyFsrs.review(null,3,start)
  assertEquals("learning",first.getString("state"))
  val graduated=StudyFsrs.review(first,3,start+600_000L)
  assertEquals("review",graduated.getString("state"))
  assertTrue(graduated.getLong("dueAt")>=start+600_000L+day)
  assertEquals(2,graduated.getJSONArray("reviews").length())
 }

 @Test fun successfulReviewProducesValidMemoryState(){
  val first=StudyFsrs.review(null,4,start)
  val next=StudyFsrs.review(first,3,start+9*day)
  assertEquals("review",next.getString("state"))
  assertTrue(next.getDouble("stability")>0.0)
  assertTrue(next.getDouble("difficulty") in 1.0..10.0)
  assertTrue(next.getLong("dueAt")>start+9*day)
 }

 @Test fun forgottenReviewEntersRelearning(){
  val initial=StudyFsrs.review(null,4,start)
  val forgotten=StudyFsrs.review(initial,1,start+12*day)
  assertEquals("relearning",forgotten.getString("state"))
  assertEquals(start+12*day+600_000L,forgotten.getLong("dueAt"))
  val recovered=StudyFsrs.review(forgotten,3,start+12*day+600_000L)
  assertEquals("review",recovered.getString("state"))
 }

 @Test fun legacyHistoryMigratesWithoutInventingReviews(){
  val old=JSONObject().put("attempts",3).put("streak",2)
   .put("dueAt",start+day).put("lastAt",start)
  val migrated=StudyFsrs.migrateLegacy(old,start)!!
  assertEquals("fsrs-6",migrated.getString("algorithm"))
  assertEquals(start+day,migrated.getLong("dueAt"))
  assertEquals(0,migrated.getJSONArray("reviews").length())
  assertEquals(3,migrated.getInt("attempts"))
 }

 @Test fun dueDateAndRetentionAreWellDefined(){
  assertEquals(0L,StudyFsrs.dueAt(null))
  val card=StudyFsrs.review(null,4,start)
  assertEquals(null,StudyFsrs.retention(card,start))
  assertTrue(StudyFsrs.retention(card,start+day)!! in 0.0..1.0)
 }
}
