package com.example.ui.screens

import android.text.format.DateFormat
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.Event
import com.example.model.Gate
import com.example.model.Sensor
import com.example.model.SocketMessage
import com.example.ui.Persona
import com.example.ui.StadiumViewModel
import kotlin.random.Random
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartStadiumApp(
    viewModel: StadiumViewModel,
    modifier: Modifier = Modifier
) {
    val currentPersona by viewModel.currentPersona.collectAsState()
    val emergencyMode by viewModel.emergencyMode.collectAsState()
    val emergencyMessage by viewModel.emergencyMessage.collectAsState()
    val socketMessages by viewModel.socketMessages.collectAsState()
    val events by viewModel.events.collectAsState()
    val activeEventId by viewModel.activeEventId.collectAsState()
    val gates by viewModel.gates.collectAsState()
    val sensors by viewModel.sensors.collectAsState()

    val activeEvent = events.firstOrNull { it.id == activeEventId }

    // Alert Sound / Pulsing Alarm color multiplier
    val infiniteTransition = rememberInfiniteTransition(label = "Pulse")
    val alertAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "AlertAlpha"
    )

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("smart_stadium_parent"),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (emergencyMode) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Text(
                            text = "SMART STADIUM",
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    // Quick Persona Selectors with Pill indicators
                    SingleChoiceSegmentedButtonRow(
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .testTag("persona_filter_row")
                    ) {
                        SegmentedButton(
                            selected = currentPersona == Persona.USER_DASHBOARD,
                            onClick = { viewModel.selectPersona(Persona.USER_DASHBOARD) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                            icon = {}
                        ) {
                            Text("USER", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        SegmentedButton(
                            selected = currentPersona == Persona.ADMIN_CONTROL,
                            onClick = { viewModel.selectPersona(Persona.ADMIN_CONTROL) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                            icon = {}
                        ) {
                            Text("ADMIN", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        SegmentedButton(
                            selected = currentPersona == Persona.ESP32_HARDWARE,
                            onClick = { viewModel.selectPersona(Persona.ESP32_HARDWARE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                            icon = {}
                        ) {
                            Text("ESP32", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        bottomBar = {
            // Socket.IO emulator console summary stream at the footer
            SocketStreamFooter(socketMessages = socketMessages)
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Main views routing
            AnimatedContent(
                targetState = currentPersona,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                },
                label = "PersonaTransit"
            ) { persona ->
                when (persona) {
                    Persona.USER_DASHBOARD -> {
                        UserDashboardScreen(
                            event = activeEvent,
                            gates = gates.filter { it.eventId == activeEventId },
                            viewModel = viewModel
                        )
                    }
                    Persona.ADMIN_CONTROL -> {
                        AdminControlScreen(
                            event = activeEvent,
                            gates = gates.filter { it.eventId == activeEventId },
                            sensors = sensors,
                            viewModel = viewModel
                        )
                    }
                    Persona.ESP32_HARDWARE -> {
                        ESP32SimulatorScreen(
                            sensors = sensors,
                            viewModel = viewModel
                        )
                    }
                }
            }

            // Real-time Emergency Sentry Mask Overlay
            if (emergencyMode) {
                EmergencyModalOverlay(
                    message = emergencyMessage,
                    alertPulseAlpha = alertAlpha,
                    gates = gates.filter { it.eventId == activeEventId },
                    onDeactivate = { viewModel.clearEmergencyState() }
                )
            }
        }
    }
}

// ==== USER PERSPECTIVE SCREEN ====
@Composable
fun UserDashboardScreen(
    event: Event?,
    gates: List<Gate>,
    viewModel: StadiumViewModel,
    modifier: Modifier = Modifier
) {
    val totalEntries = gates.sumOf { it.entries }
    val totalExits = gates.sumOf { it.exits }
    val activeCrowd = (totalEntries - totalExits).coerceAtLeast(0)
    val maxCapacity = event?.totalCapacity ?: 25000
    val occupancyPercentage = (activeCrowd.toFloat() / maxCapacity).coerceIn(0f, 1f)

    val isScanning by viewModel.isScanning.collectAsState()
    val scannedTicket by viewModel.scannedTicket.collectAsState()
    val qrResult by viewModel.qrValidationResult.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (event == null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("No active matches found. Switch to Admin to build a live event.", textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            // Premium Arena Header Card with backdrop representation
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color(0xFF1E3A8A), Color(0xFF030712))
                            )
                        )
                        .drawBehind {
                            // Custom pitch drawing representation
                            val center = size.width / 2f
                            drawCircle(
                                color = Color.White.copy(alpha = 0.05f),
                                radius = size.height / 3f,
                                center = Offset(center, size.height)
                            )
                        }
                        .padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF10B981),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                "MATCH DAY ACTIVE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = event.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                            Text(
                                text = event.venue,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.LightGray
                            )
                        }
                    }
                }
            }

            // Real-time Crowd meter + Simulated Ticket Scanner Block
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Meter
                    Card(
                        modifier = Modifier
                            .weight(1.2f)
                            .height(180.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "STADIUM CAPACITY",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(90.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { occupancyPercentage },
                                    modifier = Modifier.size(80.dp),
                                    color = if (occupancyPercentage > 0.85f) Color.Red else Color(0xFF10B981),
                                    strokeWidth = 8.dp,
                                    trackColor = Color.LightGray.copy(alpha = 0.3f),
                                    strokeCap = StrokeCap.Round,
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${(occupancyPercentage * 100).toInt()}%",
                                        fontWeight = FontWeight.Black,
                                        fontSize = 18.sp
                                    )
                                    Text("Full", fontSize = 9.sp, color = Color.Gray)
                                }
                            }

                            Text(
                                "$activeCrowd / $maxCapacity Spectators",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Ticket QR scan actions
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp)
                            .testTag("simulate_scan_module"),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "ACCESS SCANNER",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary
                            )

                            if (isScanning) {
                                LoadingScannerIndicator()
                            } else {
                                when (qrResult) {
                                    "VALID" -> {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(40.dp))
                                        Text("APPROVED", color = Color(0xFF10B981), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("Gate unlocked", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    "INVALID" -> {
                                        Icon(Icons.Default.Cancel, contentDescription = null, tint = Color.Red, modifier = Modifier.size(40.dp))
                                        Text("REJECTED", color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("Bad Ticket Code", fontSize = 10.sp, color = Color.Gray)
                                    }
                                    else -> {
                                        Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(40.dp))
                                        Text("Tap to simulate QR scan", fontSize = 10.sp, textAlign = TextAlign.Center)
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Button(
                                    onClick = { viewModel.submitScannedQR("VALID_TICKET_" + Random.nextInt(10000, 99999)) },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("scan_valid_btn"),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
                                ) {
                                    Text("Valid Ticket", fontSize = 9.sp)
                                }
                                Button(
                                    onClick = { viewModel.submitScannedQR("FAKE_TICKET_9999") },
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("scan_invalid_btn"),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                ) {
                                    Text("Fake Ticket", fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            // Real-time interactive ticket generation key sheet
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp)
                    ) {
                        Text("Simulating Hardware QR Access Logs:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "When an ESP32 or external reader registers a successful ticket scan, it makes an HTTP request to /api/validate-qr. Our cloud emulator automatically pushes events onto our live websockets, synchronizing this client.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Gate Availability & Crowd Distribution Cards
            item {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "GATE STATUS & LOAD",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${gates.size} Active Terminals",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            items(gates, key = { it.id }) { gate ->
                GateStatusCard(gate = gate)
            }
        }
    }
}

@Composable
fun LoadingScannerIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "Radar")
    val scannerStep by infiniteTransition.animateFloat(
        initialValue = -10f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "radar_line"
    )

    Box(
        modifier = Modifier
            .size(60.dp)
            .drawBehind {
                val lineY = (scannerStep / 50f) * size.height
                drawRect(
                    color = Color(0xFF10B981).copy(alpha = 0.2f),
                    size = size.copy(height = lineY.coerceIn(0f, size.height))
                )
                drawLine(
                    color = Color(0xFF10B981),
                    start = Offset(0f, lineY.coerceIn(0f, size.height)),
                    end = Offset(size.width, lineY.coerceIn(0f, size.height)),
                    strokeWidth = 4f
                )
            }
            .border(2.dp, Color.LightGray, RoundedCornerShape(4.dp))
    )
}

@Composable
fun GateStatusCard(gate: Gate) {
    val totalVolume = gate.crowdCount
    val limit = 5000 // default capacity warning limit per gate for mock
    val loadPercentage = (totalVolume.toFloat() / limit).coerceIn(0f, 1f)

    val gateColor = when {
        gate.status == "CLOSED" -> Color.Red
        gate.status == "EMERGENCY_ONLY" -> Color(0xFFEA580C) // Warning Orange
        loadPercentage > 0.8f -> Color(0xFFD97706) // Slow Amber
        else -> Color(0xFF10B981) // Normal Green
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("gate_card_${gate.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(gateColor, CircleShape)
                    )
                    Text(
                        text = gate.gateName,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = gateColor.copy(alpha = 0.15f),
                ) {
                    Text(
                        text = gate.status,
                        color = gateColor,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Column {
                    Text("Flow Statistics:", fontSize = 10.sp, color = Color.Gray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Entries: ${gate.entries}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Text("Exits: ${gate.exits}", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text("Active Inside: $totalVolume", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Load indicator bar
            LinearProgressIndicator(
                progress = { loadPercentage },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = gateColor,
                trackColor = Color.LightGray.copy(alpha = 0.2f)
            )
        }
    }
}


// ==== ADMIN CONTROL PANEL SCREEN ====
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdminControlScreen(
    event: Event?,
    gates: List<Gate>,
    sensors: List<Sensor>,
    viewModel: StadiumViewModel,
    modifier: Modifier = Modifier
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showAddGateDialog by remember { mutableStateOf(false) }

    // Emergency manual message input state
    var manualAlertMsg by remember { mutableStateOf("EMERGENCY SITUATION: Crowd warning inside West Stands. Evacuate via closest gates!") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core Control action headers
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ADMIN COMMAND DECK",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showCreateDialog = true },
                        modifier = Modifier.testTag("admin_create_event_btn"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Event", fontSize = 12.sp)
                    }
                }
            }
        }

        // Active Event quick overview status
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Current Linked Event", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
                            Text(event?.title ?: "No active events", fontWeight = FontWeight.Black, fontSize = 18.sp)
                        }
                    }
                }
            }
        }

        // Manual Emergency Broadcast Center
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0x1FDC2626)),
                border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Emergency, contentDescription = null, tint = Color.Red, modifier = Modifier.size(24.dp))
                        Text("Emergency Command Broadcast", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.Red)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = manualAlertMsg,
                        onValueChange = { manualAlertMsg = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("emergency_alert_input"),
                        label = { Text("Alarm broadcast messaging payload") },
                        textStyle = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.triggerEmergency(manualAlertMsg) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("admin_trigger_emergency_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("BROADCAST CRISIS LEVEL WARNING", fontWeight = FontWeight.Black, color = Color.White)
                    }
                }
            }
        }

        // Gate Controller Grid (Modify gate state, unlock, lock, simulate clicks)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CROWD EXITS AND GATES CONTROLLER", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                TextButton(
                    onClick = { showAddGateDialog = true },
                    modifier = Modifier.testTag("add_gate_action")
                ) {
                    Text("+ Add Gate", fontSize = 12.sp)
                }
            }
        }

        items(gates, key = { it.id }) { gate ->
            AdminGateControlCard(gate = gate, onIncrementEntry = {
                viewModel.incrementGateCount(gate.id, isEntry = true)
            }, onIncrementExit = {
                viewModel.incrementGateCount(gate.id, isEntry = false)
            }, onStatusChange = { newStatus ->
                viewModel.toggleGateState(gate.id, newStatus)
            })
        }

        // Flame and Thermal Environment Sensor Nodes
        item {
            Text("HARDWARE NETWORK SENSOR TELEMETRY", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        items(sensors, key = { it.sensorId }) { sensor ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (sensor.status == "FIRE_DETECTED") Color(0x3DDC2626) else MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(
                    1.dp,
                    if (sensor.status == "FIRE_DETECTED") Color.Red else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(sensor.location, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Node ID: ${sensor.sensorId}", fontSize = 10.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
                        if (sensor.lastTriggered != null) {
                            Text(
                                "Last alarm: " + DateFormat.format("HH:mm:ss", sensor.lastTriggered),
                                fontSize = 9.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (sensor.status == "FIRE_DETECTED") Color.Red else Color(0x1F10B981)
                    ) {
                        Text(
                            text = sensor.status,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (sensor.status == "FIRE_DETECTED") Color.White else Color(0xFF10B981),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // CREATE EVENT DIALOG
    if (showCreateDialog) {
        var eventTitle by remember { mutableStateOf("Championship Final Derby") }
        var eventVenue by remember { mutableStateOf("Lusail National Arena") }
        var capacityText by remember { mutableStateOf("45000") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Configure New Arena Event") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = eventTitle,
                        onValueChange = { eventTitle = it },
                        modifier = Modifier.testTag("new_event_title_input"),
                        label = { Text("Match Event Title") }
                    )
                    OutlinedTextField(
                        value = eventVenue,
                        onValueChange = { eventVenue = it },
                        modifier = Modifier.testTag("new_event_venue_input"),
                        label = { Text("Venue / Arena Ground") }
                    )
                    OutlinedTextField(
                        value = capacityText,
                        onValueChange = { capacityText = it },
                        modifier = Modifier.testTag("new_event_capacity_input"),
                        label = { Text("Stadium Capacity limit") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cap = capacityText.toIntOrNull() ?: 30000
                        viewModel.createEvent(eventTitle, eventVenue, cap)
                        showCreateDialog = false
                    },
                    modifier = Modifier.testTag("save_event_confirm")
                ) {
                    Text("Save Event")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Dismiss")
                }
            }
        )
    }

    // ADD GATE DIALOG
    if (showAddGateDialog) {
        var gateName by remember { mutableStateOf("Gate " + ('A'..'Z').random()) }
        var selectedStatus by remember { mutableStateOf("OPEN") }

        AlertDialog(
            onDismissRequest = { showAddGateDialog = false },
            title = { Text("Register Physical Gate Terminal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = gateName,
                        onValueChange = { gateName = it },
                        modifier = Modifier.testTag("new_gate_name_input"),
                        label = { Text("Gate Designation Name") }
                    )
                    Text("Initial Operating Configuration:")
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("OPEN", "CLOSED").forEach { status ->
                            FilterChip(
                                selected = selectedStatus == status,
                                onClick = { selectedStatus = status },
                                label = { Text(status) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addGate(gateName, selectedStatus)
                        showAddGateDialog = false
                    },
                    modifier = Modifier.testTag("save_gate_confirm")
                ) {
                    Text("Configure")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddGateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun AdminGateControlCard(
    gate: Gate,
    onIncrementEntry: () -> Unit,
    onIncrementExit: () -> Unit,
    onStatusChange: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("admin_gate_${gate.id}"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(gate.gateName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                
                // Toggle Actions configuration Row
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("OPEN", "CLOSED", "EMERGENCY_ONLY").forEach { status ->
                        val isSelected = gate.status == status
                        val col = when (status) {
                            "OPEN" -> Color(0xFF10B981)
                            "CLOSED" -> Color.Red
                            else -> Color(0xFFEA580C)
                        }
                        ElevatedButton(
                            onClick = { onStatusChange(status) },
                            modifier = Modifier.height(28.dp),
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            colors = ButtonDefaults.elevatedButtonColors(
                                containerColor = if (isSelected) col else MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        ) {
                            Text(status, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Entries: ${gate.entries} | Exits: ${gate.exits}", fontSize = 12.sp, color = Color.Gray)
                    Text("Current Load: ${gate.crowdCount}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(
                        onClick = onIncrementEntry,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("+ Simulation Check-In", fontSize = 10.sp)
                    }
                    OutlinedButton(
                        onClick = onIncrementExit,
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text("- Simulation Egress", fontSize = 10.sp)
                    }
                }
            }
        }
    }
}


// ==== ESP32 HARDWARE BOARD SIMULATOR SCREEN ====
@Composable
fun ESP32SimulatorScreen(
    sensors: List<Sensor>,
    viewModel: StadiumViewModel,
    modifier: Modifier = Modifier
) {
    var logs by remember { mutableStateOf(listOf("[ESP32 Setup] Booting Expressif platform...", "[ESP32 Wifi] Connecting local SSID...", "[ESP32 REST] Linked successfully to API endpoints.")) }

    // Let's emulate serial prints when action triggers
    val currentSensor = sensors.firstOrNull { it.sensorId == "sensor_1" }
    val isFireActive = currentSensor?.status == "FIRE_DETECTED"

    LaunchedEffect(isFireActive) {
        if (isFireActive) {
            logs = logs + "[ESP32 D4] !! TRANSITION PIN 4 STATE: LOW (FLAME DETECTED) !!"
            logs = logs + "[ESP32 REST] Sending packet to POST /api/sensors/flame-alert"
            logs = logs + "[ESP32 WebClient] Event accepted by node server: 200 OK"
        } else {
            logs = logs + "[ESP32 D4] State check: HIGH (Normal telemetry ambient)"
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                "ESP32 FLAME CONTROLLER BOARD",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontSize = 15.sp
            )
            Text(
                "Simulated representation of the microchip flame sensor unit communicating via API endpoints with standard POST payloads in real-time.",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        // ESP32 Physical Vector rendering card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("esp32_hardware_card"),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = BorderStroke(2.dp, Color(0xFF475569))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Board Head header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ESP32-WROOM-32U",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFA7F3D0),
                            fontSize = 14.sp
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // PWR Led
                            PowerLed(label = "PWR", active = true, color = Color.Red)
                            // TX/RX Led blinking simulation
                            PowerLed(label = "TXD", active = isFireActive, color = Color.Green)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Chip Microprocessor Drawing representation
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A), RoundedCornerShape(8.dp))
                            .border(1.dp, Color(0xFF334155), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column(horizontalAlignment = Alignment.Start) {
                            Text("CPU Architecture: Single Core Tensilica", color = Color.Gray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text("Sensors pins: PIN 4 (GPIO4) connected to Flame Sensor D0", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("PIN 4 status: ", color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = if (isFireActive) Color.Red else Color(0x3D10B981)
                                ) {
                                    Text(
                                        text = if (isFireActive) "LOW (FLAME DETECTED!)" else "HIGH (SAFE Telemetry)",
                                        color = if (isFireActive) Color.White else Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Interactive simulation input trigger button
                    Button(
                        onClick = {
                            viewModel.triggerESP32Flame("sensor_1", !isFireActive)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("apply_heat_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isFireActive) Color(0xFF10B981) else Color(0xFFDC2626)
                        )
                    ) {
                        Icon(
                            imageVector = if (isFireActive) Icons.Default.Check else Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isFireActive) "COOL DOWN SENSOR (Reset SAFE)" else "APPLY HEAT / SPARK ON DETECTOR (PIN 4)",
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }

        // Serial Terminal Monitor Logs
        item {
            Text("NATIVE SERIAL MONITOR FEED (115200 BAUD)", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        }

        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Black),
                border = BorderStroke(1.dp, Color(0xFF1E293B))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    reverseLayout = true
                ) {
                    items(logs.reversed()) { log ->
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = if (log.contains("!!") || log.contains("FIRE")) Color.Red else Color(0xFF34D399)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PowerLed(label: String, active: Boolean, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(if (active) color else Color.Black, CircleShape)
                .border(1.dp, Color.LightGray, CircleShape)
        )
        Text(label, fontSize = 8.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
    }
}


// === GLOBAL EMERGENCY GRAPH EVAC POPUP OVERLAY ===
@Composable
fun EmergencyModalOverlay(
    message: String,
    alertPulseAlpha: Float,
    gates: List<Gate>,
    onDeactivate: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Red.copy(alpha = 0.95f * alertPulseAlpha))
            .clickable(enabled = false) {} // block events
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(90.dp)
            )

            Text(
                text = "CRITICAL EMERGENCY WARNING",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Text(
                text = message,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // AI evacuation routing status map
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.6f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "🚨 ACTIVE SMART EVACUATION PLAN",
                        fontWeight = FontWeight.Bold,
                        color = Color.Yellow,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Evacuating spectators through least-loaded gates. Avoid locked/crowded areas.",
                        fontSize = 11.sp,
                        color = Color.LightGray
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    gates.forEach { gate ->
                        val safetyAdvice = when {
                            gate.status == "CLOSED" -> "CLOSED - AVOID REGION"
                            gate.crowdCount > 2000 -> "CONGESTED - HEAVY WAIT"
                            else -> "CLEAR - FAST ROUTE OUT"
                        }
                        val pathColor = when {
                            gate.status == "CLOSED" -> Color.Red
                            gate.crowdCount > 2000 -> Color.Yellow
                            else -> Color(0xFF10B981)
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(gate.gateName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = pathColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    safetyAdvice,
                                    color = pathColor,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onDeactivate,
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .testTag("dismiss_emergency_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Red)
            ) {
                Text("RESET EMERGENCY STATUS", fontWeight = FontWeight.Black)
            }
        }
    }
}


// === BOTTOM WEB-SOCKET FEED PANEL ===
@Composable
fun SocketStreamFooter(
    socketMessages: List<SocketMessage>
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(Color(0xFF34D399), CircleShape)
                    )
                    Text(
                        "SOCKET.IO BROADCAST ENGINE (LIVEFEED)",
                        fontWeight = FontWeight.Black,
                        fontSize = 9.sp,
                        letterSpacing = 0.5.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val lastMsg = socketMessages.firstOrNull()
                AnimatedContent(
                    targetState = lastMsg,
                    transitionSpec = {
                        slideInVertically { it } togetherWith slideOutVertically { -it }
                    },
                    label = "LogTransit"
                ) { msg ->
                    Text(
                        text = msg?.let { "[${it.eventType}] ${it.content}" } ?: "System initialized. Waiting for telemetry...",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp).testTag("last_socket_log")
                    )
                }
            }
        }
    }
}
