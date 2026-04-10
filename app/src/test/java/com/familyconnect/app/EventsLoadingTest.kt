package com.familyconnect.app

import org.junit.Test
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.familyconnect.app.data.model.FamilyEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class EventsLoadingTest {

    @Test
    fun testEventsForUserA() {
        val userAMobile = "9876543210"
        println("\n\n" + "=".repeat(70))
        println("TEST 1: Load events for User A ($userAMobile)")
        println("=".repeat(70))
        testEventsForUser(userAMobile)
    }

    @Test
    fun testEventsForUserB() {
        val userBMobile = "9765432109"
        println("\n\n" + "=".repeat(70))
        println("TEST 2: Load events for User B ($userBMobile)")
        println("=".repeat(70))
        testEventsForUser(userBMobile)
    }

    @Test
    fun testAllEventsAndFiltering() {
        println("\n\n" + "=".repeat(70))
        println("TEST 3: Load ALL events and show filtration")
        println("=".repeat(70))

        val database = FirebaseDatabase.getInstance(
            "https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app"
        )

        val latch = CountDownLatch(1)

        database.getReference("events").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                println("\n📊 Total events in Firebase: ${snapshot.childrenCount}")

                if (snapshot.childrenCount == 0L) {
                    println("⚠️  No events found!")
                    latch.countDown()
                    return
                }

                println("\n📋 All Events:")
                println("-".repeat(70))

                var eventCount = 0
                for (child in snapshot.children) {
                    eventCount++
                    try {
                        val event = child.getValue(FamilyEvent::class.java)
                        if (event != null) {
                            println("\n$eventCount. ${event.title}")
                            println("   ID: ${event.id}")
                            println("   Created By: ${event.createdBy}")
                            println("   Invited: ${event.invitedMembers}")
                        } else {
                            println("\n$eventCount. ❌ Failed to deserialize")
                        }
                    } catch (e: Exception) {
                        println("\n$eventCount. ❌ Error: ${e.message}")
                    }
                }

                println("\n" + "-".repeat(70))
                println("✅ Test completed. Total events processed: $eventCount")

                latch.countDown()
            }

            override fun onCancelled(error: DatabaseError) {
                println("❌ Firebase Error: ${error.message}")
                latch.countDown()
            }
        })

        // Wait for Firebase
        val completed = latch.await(15, TimeUnit.SECONDS)
        println("\nResult: ${if (completed) "✅ Completed" else "❌ Timeout"}")
    }

    private fun testEventsForUser(userMobile: String) {
        val database = FirebaseDatabase.getInstance(
            "https://family-connect-app-a219b-default-rtdb.asia-southeast1.firebasedatabase.app"
        )

        val latch = CountDownLatch(1)
        var matchingCount = 0
        var totalCount = 0

        database.getReference("events").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                totalCount = snapshot.childrenCount.toInt()
                println("\n📊 Total events in Firebase: $totalCount")

                if (totalCount == 0) {
                    println("⚠️  No events in Firebase!")
                    latch.countDown()
                    return
                }

                println("\n🔍 Checking which events include user $userMobile:")
                println("-".repeat(70))

                for (child in snapshot.children) {
                    val eventKey = child.key ?: "unknown"
                    try {
                        val event = child.getValue(FamilyEvent::class.java)

                        if (event != null) {
                            val isInvited = userMobile in event.invitedMembers
                            val status = if (isInvited) "✅ INVITED" else "❌ NOT INVITED"

                            println("\n📌 Event: ${event.title}")
                            println("   Key: $eventKey")
                            println("   Created By: ${event.createdBy}")
                            println("   Invited Members: ${event.invitedMembers}")
                            println("   Status for $userMobile: $status")

                            if (isInvited) {
                                matchingCount++
                            }
                        } else {
                            println("\n📌 Event $eventKey: ❌ Deserialization failed")
                        }
                    } catch (e: Exception) {
                        println("\n📌 Event $eventKey: ❌ Error - ${e.message}")
                    }
                }

                println("\n" + "-".repeat(70))
                println("\n✅ SUMMARY FOR USER: $userMobile")
                println("   Total events: $totalCount")
                println("   Events this user is invited to: $matchingCount")

                if (matchingCount > 0) {
                    println("   🎉 SUCCESS - Events loaded correctly!")
                } else {
                    println("   ⚠️  User not invited to any events (or no events exist)")
                }

                latch.countDown()
            }

            override fun onCancelled(error: DatabaseError) {
                println("❌ Firebase Error: ${error.message}")
                println("   Code: ${error.code}")
                latch.countDown()
            }
        })

        // Wait for Firebase response (max 15 seconds)
        println("\n⏳ Connecting to Firebase (15 sec timeout)...")
        val completed = latch.await(15, TimeUnit.SECONDS)

        if (!completed) {
            println("\n❌ ERROR: Firebase did not respond within 15 seconds")
        }

        println("\n" + "=".repeat(70) + "\n")
    }
}
