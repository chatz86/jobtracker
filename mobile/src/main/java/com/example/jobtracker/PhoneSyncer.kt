package com.example.jobtracker

import android.content.Context
import android.util.Log
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONArray

object PhoneSyncer {
    private const val TAG = "PhoneSync"
    private const val REQUEST_PATH = "/jobtracker/request"
    private const val CONFIG_PATH = "/jobtracker/config"
    private const val ACTIVE_PATH = "/jobtracker/active"
    private const val HISTORY_PATH = "/jobtracker/history"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun requestFullSync(context: Context) {
        scope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(context)
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.d(TAG, "No connected nodes to request sync")
                    return@launch
                }
                val messageClient = Wearable.getMessageClient(context)
                for (node in nodes) {
                    try {
                        messageClient.sendMessage(node.id, REQUEST_PATH, ByteArray(0)).await()
                        Log.d(TAG, "Requested full sync from ${node.displayName}")
                    } catch (e: Exception) {
                        Log.e(TAG, "Request send failed to ${node.displayName}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Request sync error", e)
            }
        }
    }

    fun syncConfig(context: Context, wards: List<String>, attendees: List<String>) {
        scope.launch {
            send(context, CONFIG_PATH, "WARDS:${JSONArray(wards).toString()}||ATTENDEES:${JSONArray(attendees).toString()}")
        }
    }

    fun syncActive(context: Context, entry: PhoneActiveEntry?) {
        scope.launch {
            val payload = entry?.toJson()?.toString() ?: "null"
            send(context, ACTIVE_PATH, payload)
        }
    }

    fun syncHistory(context: Context, history: List<PhoneHistoryRecord>) {
        scope.launch {
            val arr = JSONArray()
            history.forEach { arr.put(it.toJson()) }
            send(context, HISTORY_PATH, arr.toString())
        }
    }

    private fun send(context: Context, path: String, payload: String) {
        scope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(context)
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.d(TAG, "No connected nodes for $path")
                    return@launch
                }
                val bytes = payload.toByteArray(java.nio.charset.StandardCharsets.UTF_8)
                val messageClient = Wearable.getMessageClient(context)
                for (node in nodes) {
                    try {
                        messageClient.sendMessage(node.id, path, bytes).await()
                        Log.d(TAG, "Sent $path to ${node.displayName} (${bytes.size} bytes)")
                    } catch (e: Exception) {
                        Log.e(TAG, "Send failed to ${node.displayName}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send error on $path", e)
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }
}