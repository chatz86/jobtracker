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

object PhoneSyncer {
    private const val TAG = "PhoneSync"
    private const val REQUEST_PATH = "/jobtracker/request"
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

    fun destroy() {
        scope.cancel()
    }
}