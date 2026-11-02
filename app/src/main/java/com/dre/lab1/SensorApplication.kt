package com.dre.lab1

import android.app.Application
import com.dre.lab1.helpers.NetworkMonitor
import com.dre.lab1.repository.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import java.util.UUID

class SensorApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val sensorRepository: SensorRepository by lazy {
        val prefs = getSharedPreferences("sensor_installation", MODE_PRIVATE)
        val id = prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("id", it).apply()
        }
        LatestSensorRepository(id, NetworkMonitor(this).online, scope) { sampleId, data ->
            check(FirebaseApp.initializeApp(this) != null) {
                "Firebase is not configured. Add app/google-services.json."
            }
            val auth = FirebaseAuth.getInstance()
            val user = auth.currentUser ?: auth.signInAnonymously().await().user
                ?: error("Anonymous authentication failed")
            // Persistence remains disabled: only the app's latest pending sample is retained.
            FirebaseDatabase.getInstance().getReference("users").child(user.uid)
                .child("sensorData").child(sampleId).setValue(data.toPayload()).await()
        }
    }
}
