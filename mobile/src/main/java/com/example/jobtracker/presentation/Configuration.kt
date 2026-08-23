package com.example.jobtracker.presentation

/**
 * Edit this file to update your staff, locations, and OneNote sync settings.
 */
object Configuration {
    // Types of activities you track
    val entryTypes = listOf("Job", "Patrol")

    // Your predefined locations
    val wards = listOf(
        "Ward A",
        "Ward B",
        "High Acuity",
        "Emergency",
        "Maternity",
        "Intensive Care",
        "Radiology"
    )

    // Your staff members
    val attendees = listOf(
        "Dr. Smith",
        "Nurse Jane",
        "Security Team",
        "Dr. Jones",
        "Staff Member A",
        "Staff Member B"
    )

    /**
     * PIN required to delete history or list items.
     */
    const val DELETION_PIN = "1956"
}
