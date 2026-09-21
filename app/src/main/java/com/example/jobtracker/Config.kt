package com.example.jobtracker

import java.security.MessageDigest
import java.util.Locale

object Config {
    val ENTRY_TYPES = listOf("Job", "Foot Patrol", "Vehicle Patrol")

    // The PIN itself is never stored in source or shipped as a readable string
    // constant; only a salted SHA-256 digest is kept and compared against input.
    private const val DELETION_PIN_SALT = "JobTracker::deletion-pin::"
    private const val DELETION_PIN_HASH =
        "b1a9e1a7dcc1455e9123059d163d4141e0fa461a70945019fec4619a6629f6e4"

    fun isDeletionPin(input: String): Boolean =
        sha256Hex(DELETION_PIN_SALT + input).equals(DELETION_PIN_HASH, ignoreCase = true)

    private fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> String.format(Locale.US, "%02x", byte) }

    const val SYNC_PATH_CONFIG = "/jobtracker/config"
    const val SYNC_PATH_ACTIVE = "/jobtracker/active"
    const val SYNC_PATH_HISTORY = "/jobtracker/history"
    const val SYNC_PATH_REQUEST = "/jobtracker/request"
    const val PREFS_NAME = "jobtracker_prefs"
}

