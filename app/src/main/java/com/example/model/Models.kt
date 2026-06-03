package com.example.model

import java.util.Date

data class Event(
    val id: String,
    val title: String,
    val venue: String,
    val bannerUrl: String,
    val startTime: Date,
    val gates: List<String> = emptyList(),
    val emergencyMode: Boolean = false,
    val totalCapacity: Int = 25000
)

data class Gate(
    val id: String,
    val eventId: String,
    val gateName: String,
    val entries: Int = 0,
    val exits: Int = 0,
    val status: String = "OPEN" // "OPEN", "CLOSED", "EMERGENCY_ONLY"
) {
    val crowdCount: Int get() = (entries - exits).coerceAtLeast(0)
}

data class Sensor(
    val sensorId: String,
    val location: String,
    val status: String = "SAFE", // "SAFE", "FIRE_DETECTED"
    val lastTriggered: Date? = null
)

data class SocketMessage(
    val id: String,
    val timestamp: Long,
    val eventType: String, // "GATE_UPDATE", "FIRE_ALERT", "EMERGENCY_BROADCAST", "SYSTEM"
    val content: String
)
