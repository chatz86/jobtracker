package com.example.jobtracker

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

object Syncer {
    private const val TAG = "JobTrackerSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun send(context: Context, path: String, payload: String) {
        scope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(context)
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.d(TAG, "No connected nodes for $path")
                    return@launch
                }
                val bytes = payload.toByteArray(Charsets.UTF_8)
                val messageClient = Wearable.getMessageClient(context)
                for (node in nodes) {
                    try {
                        messageClient.sendMessage(node.id, path, bytes).await()
                        Log.d(TAG, "Sent $path to ${node.displayName} (${bytes.size} bytes)")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Send failed to ${node.displayName}: ${e.message}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Send error on $path", e)
            }
        }
    }

    fun syncConfig(context: Context) {
        val wards = Persistence.getWards(context)
        val attendees = Persistence.getAttendees(context)
        val payload = "WARDS:${JSONArray(wards).toString()}||ATTENDEES:${JSONArray(attendees).toString()}"
        send(context, Config.SYNC_PATH_CONFIG, payload)
    }

    fun syncActive(context: Context) {
        val entry = Persistence.getActiveEntry(context)
        val payload = entry?.toJson()?.toString() ?: "null"
        send(context, Config.SYNC_PATH_ACTIVE, payload)
    }

    fun syncHistory(context: Context) {
        val history = Persistence.getHistory(context)
        val arr = JSONArray()
        history.forEach { arr.put(it.toJson()) }
        send(context, Config.SYNC_PATH_HISTORY, arr.toString())
    }

    fun syncAll(context: Context) {
        syncConfig(context)
        syncActive(context)
        syncHistory(context)
    }

    fun destroy() {
        scope.cancel()
    }
}