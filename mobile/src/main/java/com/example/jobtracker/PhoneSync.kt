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

object PhoneSync {
    private const val TAG = "PhoneSync"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun request(context: Context) {
        scope.launch {
            try {
                val nodeClient = Wearable.getNodeClient(context)
                val nodes = nodeClient.connectedNodes.await()
                if (nodes.isEmpty()) {
                    Log.d(TAG, "No connected nodes, cannot request sync")
                    return@launch
                }
                val messageClient = Wearable.getMessageClient(context)
                for (node in nodes) {
                    try {
                        messageClient.sendMessage(node.id, Config.SYNC_PATH_REQUEST, ByteArray(0)).await()
                        Log.d(TAG, "Requested sync from ${node.displayName}")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.e(TAG, "Request failed to ${node.displayName}: ${e.message}")
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Request error", e)
            }
        }
    }

    fun destroy() {
        scope.cancel()
    }
}