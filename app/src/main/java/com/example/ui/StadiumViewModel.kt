package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.Event
import com.example.model.Gate
import com.example.model.Sensor
import com.example.model.SocketMessage
import com.example.service.StadiumAudioPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.random.Random

enum class Persona {
    USER_DASHBOARD,
    ADMIN_CONTROL,
    ESP32_HARDWARE
}

class StadiumViewModel : ViewModel() {

    private val audioPlayer = StadiumAudioPlayer()

    // Personas & Navigation
    private val _currentPersona = MutableStateFlow(Persona.USER_DASHBOARD)
    val currentPersona: StateFlow<Persona> = _currentPersona.asStateFlow()

    // Core Data States
    private val _events = MutableStateFlow<List<Event>>(emptyList())
    val events: StateFlow<List<Event>> = _events.asStateFlow()

    private val _activeEventId = MutableStateFlow<String>("")
    val activeEventId: StateFlow<String> = _activeEventId.asStateFlow()

    private val _gates = MutableStateFlow<List<Gate>>(emptyList())
    val gates: StateFlow<List<Gate>> = _gates.asStateFlow()

    private val _sensors = MutableStateFlow<List<Sensor>>(emptyList())
    val sensors: StateFlow<List<Sensor>> = _sensors.asStateFlow()

    // Global Alarm Configuration
    private val _emergencyMode = MutableStateFlow(false)
    val emergencyMode: StateFlow<Boolean> = _emergencyMode.asStateFlow()

    private val _emergencyMessage = MutableStateFlow("")
    val emergencyMessage: StateFlow<String> = _emergencyMessage.asStateFlow()

    // Real-time Event Stream (Socket.IO Emulator Logs)
    private val _socketMessages = MutableStateFlow<List<SocketMessage>>(emptyList())
    val socketMessages: StateFlow<List<SocketMessage>> = _socketMessages.asStateFlow()

    // QR State Scan Interaction
    private val _scannedTicket = MutableStateFlow<String?>(null)
    val scannedTicket: StateFlow<String?> = _scannedTicket.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _qrValidationResult = MutableStateFlow<String?>(null) // "VALID", "INVALID", "EXPIRED"
    val qrValidationResult: StateFlow<String?> = _qrValidationResult.asStateFlow()

    // Background jobs
    private var flowSimulationJob: Job? = null
    private var alarmToneJob: Job? = null

    init {
        loadMockDatabase()
        startCrowdSimulation()
        addSocketMessage("SYSTEM", "Socket.IO server listening on port 5000")
        addSocketMessage("SYSTEM", "ESP32 Node server linked via local Wi-Fi gateway")
    }

    private fun loadMockDatabase() {
        val eventId = "evt_001"
        _activeEventId.value = eventId

        _events.value = listOf(
            Event(
                id = eventId,
                title = "Champions League Football Finals",
                venue = "Lusail Grand Stadium",
                bannerUrl = "football_stadium",
                startTime = Date(System.currentTimeMillis() + 7200000), // 2 hours from now
                gates = listOf("Gate A (North)", "Gate B (South)", "Gate C (East)", "Gate D (Emergency)")
            )
        )

        _gates.value = listOf(
            Gate("gate_a", eventId, "Gate A (North)", entries = 4850, exits = 1200, status = "OPEN"),
            Gate("gate_b", eventId, "Gate B (South)", entries = 5900, exits = 960, status = "OPEN"),
            Gate("gate_c", eventId, "Gate C (East)", entries = 3120, exits = 420, status = "OPEN"),
            Gate("gate_d", eventId, "Gate D (Emergency)", entries = 300, exits = 120, status = "CLOSED")
        )

        _sensors.value = listOf(
            Sensor("sensor_1", "Gate A - North Evacuation Zone", "SAFE"),
            Sensor("sensor_2", "Gate B - South Entrance Ring", "SAFE"),
            Sensor("sensor_3", "Concourse - Fast Food Plaza", "SAFE")
        )
    }

    private fun startCrowdSimulation() {
        flowSimulationJob?.cancel()
        flowSimulationJob = viewModelScope.launch {
            while (isActive) {
                delay(3500 + Random.nextLong(2000))
                
                // Do not randomly accumulate entry crowd during severe emergency (everyone is evacuating!)
                if (_emergencyMode.value) {
                    // Evacuation simulation: increase exits dramatically at open gates
                    _gates.value = _gates.value.map { gate ->
                        if (gate.status != "CLOSED" && gate.crowdCount > 0) {
                            val evacVolume = Random.nextInt(12, 35)
                            val finalExits = gate.exits + evacVolume
                            addSocketMessage(
                                "GATE_UPDATE",
                                "EMERGENCY EVAC: ${gate.gateName} registered ${evacVolume} rapid egress logs"
                            )
                            gate.copy(exits = finalExits, status = "EMERGENCY_ONLY")
                        } else {
                            gate
                        }
                    }
                } else {
                    // Normal behavior: spectators trickle in and out
                    _gates.value = _gates.value.map { gate ->
                        if (gate.status == "OPEN" && Random.nextFloat() > 0.3f) {
                            val addEntry = Random.nextInt(1, 8)
                            val addExit = Random.nextInt(1, 4)
                            val finalEntries = gate.entries + addEntry
                            val finalExits = gate.exits + addExit
                            
                            if (Random.nextFloat() > 0.7f) {
                                addSocketMessage(
                                    "GATE_UPDATE",
                                    "Real-time update: ${gate.gateName} counted +${addEntry} in, -${addExit} out"
                                )
                            }
                            gate.copy(entries = finalEntries, exits = finalExits)
                        } else {
                            gate
                        }
                    }
                }
            }
        }
    }

    private fun addSocketMessage(eventType: String, content: String) {
        val message = SocketMessage(
            id = "msg_${System.currentTimeMillis()}_${Random.nextInt(1000)}",
            timestamp = System.currentTimeMillis(),
            eventType = eventType,
            content = content
        )
        // Keep logs capped at 100 entries
        _socketMessages.value = (listOf(message) + _socketMessages.value).take(100)
    }

    fun selectPersona(persona: Persona) {
        _currentPersona.value = persona
    }

    // CRUD/Admin Actions
    fun createEvent(title: String, venue: String, capacity: Int) {
        val newId = "evt_${System.currentTimeMillis()}"
        val newEvent = Event(
            id = newId,
            title = title,
            venue = venue,
            bannerUrl = "arena",
            startTime = Date(System.currentTimeMillis() + 86400000), // tomorrow
            gates = emptyList(),
            totalCapacity = capacity
        )
        _events.value = _events.value + newEvent
        _activeEventId.value = newId
        
        // Setup initial gates for new event
        _gates.value = listOf(
            Gate("gate_new_a", newId, "General Gate 1", 0, 0, "OPEN"),
            Gate("gate_new_b", newId, "VIP East Entrance", 0, 0, "OPEN"),
            Gate("gate_new_c", newId, "Emergency Gate 3", 0, 0, "CLOSED")
        )
        
        addSocketMessage("SYSTEM", "New Event Created: '$title' at $venue. Loaded default gate routing.")
    }

    fun addGate(gateName: String, initialStatus: String) {
        val activeId = _activeEventId.value
        val newGateId = "gate_${System.currentTimeMillis()}"
        val newGate = Gate(
            id = newGateId,
            eventId = activeId,
            gateName = gateName,
            entries = 0,
            exits = 0,
            status = initialStatus
        )
        _gates.value = _gates.value + newGate
        
        // Update Event Gate listings
        _events.value = _events.value.map { e ->
            if (e.id == activeId) {
                e.copy(gates = e.gates + gateName)
            } else e
        }
        
        addSocketMessage("SYSTEM", "New physical Gate configured: $gateName ($initialStatus)")
    }

    fun toggleGateState(gateId: String, newStatus: String) {
        _gates.value = _gates.value.map { g ->
            if (g.id == gateId) {
                addSocketMessage("SYSTEM", "Gate ${g.gateName} status overridden to $newStatus")
                g.copy(status = newStatus)
            } else g
        }
    }

    fun incrementGateCount(gateId: String, isEntry: Boolean) {
        _gates.value = _gates.value.map { g ->
            if (g.id == gateId) {
                audioPlayer.playScanSuccess()
                if (isEntry) {
                    addSocketMessage("GATE_UPDATE", "[HTTP POST/update] ${g.gateName} recorded single entry check-in")
                    g.copy(entries = g.entries + 1)
                } else {
                    addSocketMessage("GATE_UPDATE", "[HTTP POST/update] ${g.gateName} recorded single exit release")
                    g.copy(exits = g.exits + 1)
                }
            } else g
        }
    }

    // Interactive simulated scan
    fun startQRScan() {
        _isScanning.value = true
        _qrValidationResult.value = null
        _scannedTicket.value = null
    }

    fun submitScannedQR(content: String) {
        viewModelScope.launch {
            _isScanning.value = false
            _scannedTicket.value = content
            
            delay(800) // process simulation latency
            if (content.startsWith("VALID_TICKET_")) {
                _qrValidationResult.value = "VALID"
                audioPlayer.playScanSuccess()
                
                // Automatically increment primary entry gate (Gate A or B)
                val targetGate = _gates.value.firstOrNull { g -> g.status == "OPEN" }
                targetGate?.let { gate ->
                    _gates.value = _gates.value.map { g ->
                        if (g.id == gate.id) {
                            g.copy(entries = g.entries + 1)
                        } else g
                    }
                    addSocketMessage("GATE_UPDATE", "QR Access validated: Spectator scanned at ${gate.gateName}")
                }
            } else {
                _qrValidationResult.value = "INVALID"
                // Play buzzer sound/action
                audioPlayer.playEmergencyAlert()
                addSocketMessage("SYSTEM", "WARNING: Ticket verification failed. Invalid scan raw payload = '$content'")
            }
        }
    }

    fun resetScanState() {
        _scannedTicket.value = null
        _qrValidationResult.value = null
        _isScanning.value = false
    }

    // Hardware Node simulated calls (e.g. from Pin 4 on Flame sensor)
    fun triggerESP32Flame(sensorId: String, shouldTrigger: Boolean) {
        val targetSensor = _sensors.value.firstOrNull { it.sensorId == sensorId } ?: return
        
        _sensors.value = _sensors.value.map { s ->
            if (s.sensorId == sensorId) {
                s.copy(
                    status = if (shouldTrigger) "FIRE_DETECTED" else "SAFE",
                    lastTriggered = if (shouldTrigger) Date() else s.lastTriggered
                )
            } else s
        }

        if (shouldTrigger) {
            // Emulate ESP32 making HTTP POST /api/sensors/flame-alert
            addSocketMessage(
                "FIRE_ALERT",
                "🔥 ALERT [ESP32 Pin 4]: Ignition trigger at ${targetSensor.location}! Status: FIRE_DETECTED"
            )
            // Trigger emergency mode automatically for safety simulation!
            triggerEmergency("CRITICAL FIRE DETECTED: Thermal sensor at ${targetSensor.location} triggered automatic safety protocols. PLEASE EVACUATE IMMEDIATELY!")
        } else {
            addSocketMessage(
                "SYSTEM",
                "ESP32 Flame Sensor reset: telemetry reporting SAFE status at ${targetSensor.location}"
            )
        }
    }

    // Emergency Alerts Broadcast
    fun triggerEmergency(message: String) {
        _emergencyMode.value = true
        _emergencyMessage.value = message
        
        // Set all closed or slow gates to EMERGENCY_ONLY to support rapid egress routing!
        _gates.value = _gates.value.map { g ->
            if (g.status == "CLOSED") {
                addSocketMessage("SYSTEM", "Safety override: Unlocking ${g.gateName} for EMERGENCY EVACUATION")
                g.copy(status = "EMERGENCY_ONLY")
            } else {
                g.copy(status = "EMERGENCY_ONLY")
            }
        }

        addSocketMessage("EMERGENCY_BROADCAST", "🚨 BROADCAST: $message")
        
        // Initiate hazard beeping
        startEmergencySiren()
    }

    fun clearEmergencyState() {
        _emergencyMode.value = false
        _emergencyMessage.value = ""
        stopEmergencySiren()
        
        // Reset gates back to normal
        _gates.value = _gates.value.mapIndexed { index, g ->
            if (g.gateName.contains("Emergency") || g.gateName.contains("3")) {
                g.copy(status = "CLOSED")
            } else {
                g.copy(status = "OPEN")
            }
        }
        
        // Reset all fire sensors
        _sensors.value = _sensors.value.map { it.copy(status = "SAFE") }
        
        addSocketMessage("SYSTEM", "Emergency status cleared. System normal state restored.")
    }

    private fun startEmergencySiren() {
        alarmToneJob?.cancel()
        alarmToneJob = viewModelScope.launch {
            while (isActive && _emergencyMode.value) {
                audioPlayer.playPanicAlarm()
                delay(3000)
            }
        }
    }

    private fun stopEmergencySiren() {
        alarmToneJob?.cancel()
        alarmToneJob = null
    }

    override fun onCleared() {
        super.onCleared()
        flowSimulationJob?.cancel()
        alarmToneJob?.cancel()
    }
}
