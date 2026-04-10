// Add this to run the Firebase test as a Gradle task
// Place in the root build.gradle.kts or run as standalone

task("testFirebaseEvents") {
    doLast {
        val inviteePhone = project.properties["phone"] as? String ?: "9876543210"
        println("Testing events for: $inviteePhone")
        
        val testFile = file("EventsTestFirebase.kt")
        if (testFile.exists()) {
            exec {
                commandLine("kotlin", testFile.absolutePath, inviteePhone)
            }
        } else {
            println("ERROR: EventsTestFirebase.kt not found")
        }
    }
}
