package com.familyconnect.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.familyconnect.app.data.model.FamilyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class EventsFirebaseTest {

    private val database = FirebaseDatabase.getInstance(
        "https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app"
    )

    @Test
    fun testLoadEventsForUserA() {
        val userAMobile = "9876543210"
        println("\n\n╔" + "═".repeat(80) + "╗")
        println("║ TEST: Load events for User A ($userAMobile)".padEnd(82) + "║")
        println("╚" + "═".repeat(80) + "╝")
        
        val events = loadEventsForUser(userAMobile)
        println("\n✅ User A can see ${events.size} events")
    }

    @Test
    fun testLoadEventsForUserB() {
        val userBMobile = "9765432109"  
        println("\n\n╔" + "═".repeat(80) + "╗")
        println("║ TEST: Load events for User B ($userBMobile)".padEnd(82) + "║")
        println("╚" + "═".repeat(80) + "╝")
        
        val events = loadEventsForUser(userBMobile)
        println("\n✅ User B can see ${events.size} events")
    }

    @Test
    fun testDemoEventRetrievalBothUsers() {
        println("\n\n╔" + "═".repeat(80) + "╗")
        println("║ COMPREHENSIVE TEST: Events retrieval for both users".padEnd(82) + "║")
        println("╚" + "═".repeat(80) + "╝")

        val userA = "9876543210"
        val userB = "9765432109"

        println("\n🔹 STEP 1: Fetch ALL events from Firebase")
        println("-".repeat(82))

        val latch = CountDownLatch(1)
        val allEventsRaw = mutableListOf<Map<String, Any?>>()

        database.getReference("events").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                println("✅ Connected to Firebase")
                println("📊 Total event records: ${snapshot.childrenCount}")

                if (snapshot.childrenCount == 0L) {
                    println("⚠️  ERROR: No events in Firebase!")
                    latch.countDown()
                    return
                }

                var successCount = 0
                var failCount = 0

                for (child in snapshot.children) {
                    try {
                        val event = child.getValue(FamilyEvent::class.java)
                        if (event != null) {
                            successCount++
                            allEventsRaw.add(mapOf(
                                "event" to event,
                                "key" to child.key
                            ))
                        } else {
                            failCount++
                            println("  ❌ Event ${child.key}: Deserialization returned null")
                        }
                    } catch (e: Exception) {
                        failCount++
                        println("  ❌ Event ${child.key}: ${e.message}")
                    }
                }

                println("✅ Successfully loaded: $successCount events")
                if (failCount > 0) println("❌ Failed to load: $failCount events")

                latch.countDown()
            }

            override fun onCancelled(error: DatabaseError) {
                println("❌ Firebase Error: ${error.message}")
                latch.countDown()
            }
        })

        latch.await(20, TimeUnit.SECONDS)

        println("\n\n🔹 STEP 2: Filter events for User A ($userA)")
        println("-".repeat(82))
        
        val userAEvents = filterEventsForUser(allEventsRaw, userA)
        println("👤 User A can access: ${userAEvents.size} events")
        userAEvents.forEachIndexed { i, e ->
            println("   ${i + 1}. ${(e["event"] as FamilyEvent).title}")
        }

        println("\n\n🔹 STEP 3: Filter events for User B ($userB)")
        println("-".repeat(82))
        
        val userBEvents = filterEventsForUser(allEventsRaw, userB)
        println("👤 User B can access: ${userBEvents.size} events")
        userBEvents.forEachIndexed { i, e ->
            println("   ${i + 1}. ${(e["event"] as FamilyEvent).title}")
        }

        println("\n\n" + "═".repeat(82))
        println("FINAL RESULT:")
        println("  Firebase total: ${allEventsRaw.size}")
        println("  User A ($userA): ${userAEvents.size} events")
        println("  User B ($userB): ${userBEvents.size} events")
        println("═".repeat(82) + "\n")
    }

    private fun loadEventsForUser(userMobile: String): List<FamilyEvent> {
        val latch = CountDownLatch(1)
        val events = mutableListOf<FamilyEvent>()

        println("\n⏳ Loading events for user: $userMobile")
        println("-".repeat(82))

        database.getReference("events").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                println("📊 Total records in Firebase: ${snapshot.childrenCount}")

                for (child in snapshot.children) {
                    try {
                        val event = child.getValue(FamilyEvent::class.java)
                        if (event != null) {
                            if (userMobile in event.invitedMembers) {
                                events.add(event)
                                println("✅ Found: ${event.title} (By: ${event.createdBy})")
                            }
                        }
                    } catch (e: Exception) {
                        println("❌ Parse error: ${e.message}")
                    }
                }

                println("\n📋 Total events for $userMobile: ${events.size}")
                latch.countDown()
            }

            override fun onCancelled(error: DatabaseError) {
                println("❌ Error: ${error.message}")
                latch.countDown()
            }
        })

        latch.await(20, TimeUnit.SECONDS)
        return events
    }

    private fun filterEventsForUser(
        allEventsRaw: List<Map<String, Any?>>,
        userMobile: String
    ): List<Map<String, Any?>> {
        return allEventsRaw.filter { eventMap ->
            val event = eventMap["event"] as? FamilyEvent
            event != null && userMobile in event.invitedMembers
        }
    }
}
