package com.dre.lab1.repository

import com.dre.lab1.helpers.ActuatorCommand
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll

data class RemoteUpdate(
    val command: ActuatorCommand = ActuatorCommand(),
    val connected: Boolean = false,
    val path: String = "",
    val error: String? = null
)

// Missing flags mean OFF; malformed values reject the whole command.
fun parseRemoteCommand(value: Any?): ActuatorCommand {
    if (value == null) return ActuatorCommand()
    require(value is Map<*, *>) { "Команда повинна бути JSON-об’єктом" }
    fun flag(key: String): Boolean {
        val field = value[key] ?: return false
        require(field is Boolean) { "$key повинен мати тип Boolean" }
        return field
    }
    return ActuatorCommand(flag("siren"), flag("vibration"), flag("flashlight"))
}

fun interface RemoteCommandRepository {
    fun commands(): Flow<RemoteUpdate>
}

class FirebaseRemoteCommandRepository(
    private val reference: suspend () -> DatabaseReference
) : RemoteCommandRepository {
    override fun commands(): Flow<RemoteUpdate> = flow {
        val ref = reference()
        emitAll(callbackFlow {
            val path = ref.toString()
            var connected = false
            var command = ActuatorCommand()
            var valid = false
            var error: String? = null
            fun publish() {
                // Cached commands must not keep outputs on while Firebase is disconnected.
                trySend(RemoteUpdate(if (connected && valid) command else ActuatorCommand(),
                    connected && valid, path, error))
            }
            val values = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    runCatching { parseRemoteCommand(snapshot.value) }
                        .onSuccess { command = it; valid = true; error = null }
                        .onFailure { valid = false; error = it.message }
                    publish()
                }
                override fun onCancelled(failure: DatabaseError) { close(failure.toException()) }
            }
            val connectionRef = ref.root.child(".info/connected")
            val connection = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    connected = snapshot.getValue(Boolean::class.java) == true
                    publish()
                }
                override fun onCancelled(failure: DatabaseError) { close(failure.toException()) }
            }
            try {
                connectionRef.addValueEventListener(connection)
                ref.addValueEventListener(values)
                awaitClose { }
            } finally {
                // Leaving Remote mode or pausing the screen removes both listeners.
                ref.removeEventListener(values)
                connectionRef.removeEventListener(connection)
            }
        }.distinctUntilChanged())
    }
}
