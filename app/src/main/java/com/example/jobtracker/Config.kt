package com.example.jobtracker

object Config {
    val ENTRY_TYPES = listOf("Job", "Patrol")
    const val DELETION_PIN = "1956"
    const val SYNC_PATH_CONFIG = "/jobtracker/config"
    const val SYNC_PATH_ACTIVE = "/jobtracker/active"
    const val SYNC_PATH_HISTORY = "/jobtracker/history"
    const val SYNC_PATH_REQUEST = "/jobtracker/request"
    const val PREFS_NAME = "jobtracker_prefs"
}
