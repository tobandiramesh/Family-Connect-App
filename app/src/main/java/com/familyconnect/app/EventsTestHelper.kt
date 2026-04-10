package com.familyconnect.app

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.familyconnect.app.data.model.FamilyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Standalone test to verify Firebase events loading for a specific invitee
 * Usage: Create instance and call testEventsForUser("9876543210")
 */
class EventsTestHelper {
    private val database = FirebaseDatabase.getInstance(
        "https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app"
    )

    fun testEventsForUser(inviteeMobile: String): List<FamilyEvent> {
        println("\n" + "=".repeat(60))
        println("🔍 TESTING EVENTS FOR USER: $inviteeMobile")
        println("=".repeat(60))

        val latch = CountDownLatch(1)
        val matchingEvents = mutableListOf<FamilyEvent>()
        var totalEvents = 0

        val eventsRef = database.getReference("events")
        println("⏳ Fetching from Firebase...")

        eventsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    totalEvents = snapshot.childrenCount.toInt()
                    println("✅ Connected! Total events: $totalEvents\n")

                    if (totalEvents == 0) {
                        println("⚠️  No events in Firebase")
                        latch.countDown()
                        return
                    }

                    println("📋 Processing events:")
                    println("-".repeat(60))

                    for (child in snapshot.children) {
                        val eventId = child.key
                        println("\n🔹 Event $eventId:")

                        try {
                            val event = child.getValue(FamilyEvent::class.java)

                            if (event != null) {
                                println("  ✅ Title: ${event.title}")
                                println("  👤 Created By: ${event.createdBy}")
                                println("  👥 Invited Members: ${event.invitedMembers}")

                                val isInvited = inviteeMobile in event.invitedMembers
                                if (isInvited) {
                                    println("  ✅✅ USER IS INVITED!")
                                    matchingEvents.add(event)
                                } else {
                                    println("  ❌ User not invited")
                                }
                            } else {
                                println("  ❌ Failed to deserialize")
                                
                                // Show raw data
                                val raw = child.value as? Map<*, *>
                                if (raw != null) {
                                    println("  📝 Raw invitedMembers type: ${raw["invitedMembers"]?.javaClass?.simpleName}")
                                }
                            }
                        } catch (e: Exception) {
                            println("  ❌ Error: ${e.message}")
                        }
                    }

                    println("\n" + "-".repeat(60))
                    println("\n📊 RESULTS:")
                    println("  Total events: $totalEvents")
                    println("  Matching events for $inviteeMobile: ${matchingEvents.size}")

                    if (matchingEvents.isNotEmpty()) {
                        println("\n✅ MATCHING EVENTS:")
                        matchingEvents.forEachIndexed { i, event ->
                            println("  ${i + 1}. ${event.title} (by ${event.createdBy})")
                        }
                    } else {
                        println("\n❌ No events found for this user")
                    }

                } catch (e: Exception) {
                    println("❌ Error: ${e.message}")
                    e.printStackTrace()
                }

                latch.countDown()
            }

            override fun onCancelled(error: DatabaseError) {
                println("❌ Firebase Error: ${error.message}")
                latch.countDown()
            }
        })

        // Wait for response
        println("⏳ Waiting for Firebase (10 sec timeout)...\n")
        latch.await(10, TimeUnit.SECONDS)

        println("\n" + "=".repeat(60))
        return matchingEvents
    }
}

// Usage example (run this from Android Studio console or logcat)
fun testEventRetrieval() {
    val helper = EventsTestHelper()
    
    // Test for User A
    println("\n\n🔹 TEST 1: User A's invited events")
    val userAEvents = helper.testEventsForUser("9876543210")
    
    println("\n\n🔹 TEST 2: User B's invited events")
    val userBEvents = helper.testEventsForUser("9765432109")
    
    println("\n\n📊 FINAL SUMMARY:")
    println("User A (9876543210): ${userAEvents.size} events")
    println("User B (9765432109): ${userBEvents.size} events")
}
