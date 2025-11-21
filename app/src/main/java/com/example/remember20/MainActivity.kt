package com.example.remember20

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import java.text.SimpleDateFormat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.remember20.ui.theme.Remember20Theme
import java.util.*
import java.util.regex.Pattern

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 101)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            if (!notificationManager.canUseFullScreenIntent()) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                try {
                    startActivity(intent)
                    Toast.makeText(this, "Por favor, permite las notificaciones a pantalla completa para las alarmas", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    // Fallback or ignore if activity not found
                }
            }
        }

    
        setContent {
            Remember20Theme {
                Box(modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFF2D0036), Color(0xFF000000)),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )) {
                    // Signature
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 40.dp, end = 20.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(
                            text = "Remember",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontFamily = FontFamily.Serif,
                                fontWeight = FontWeight.Light,
                                letterSpacing = 2.sp
                            ),
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "Roma",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.Cursive,
                                fontWeight = FontWeight.Bold,
                                fontStyle = FontStyle.Italic
                            ),
                            color = Color.White.copy(alpha = 0.3f)
                        )
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        containerColor = Color.Transparent
                    ) { innerPadding ->
                        val pagerState = rememberPagerState(pageCount = { 2 })
                        HorizontalPager(state = pagerState, modifier = Modifier.padding(innerPadding)) { page ->
                            when (page) {
                                0 -> ChatScreen()
                                1 -> CalendarScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}

data class Message(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val reminderTime: Long? = null,
    val reminderContent: String? = null
)

class ChatViewModel(application: android.app.Application) : androidx.lifecycle.AndroidViewModel(application) {
    var messages by mutableStateOf(listOf<Message>())
        private set

    var pendingAlarmTime by mutableStateOf<Long?>(null)
        private set

    private val sharedPreferences = application.getSharedPreferences("remember20_prefs", Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()

    init {
        loadMessages()
    }

    private fun loadMessages() {
        val json = sharedPreferences.getString("messages", null)
        if (json != null) {
            val type = object : com.google.gson.reflect.TypeToken<List<Message>>() {}.type
            messages = gson.fromJson(json, type)
        }
    }

    private fun saveMessages() {
        val json = gson.toJson(messages)
        sharedPreferences.edit().putString("messages", json).apply()
    }

    fun setPendingAlarm(time: Long?) {
        pendingAlarmTime = time
    }

    fun addMessage(message: Message) {
        messages = messages + message
        saveMessages()
    }

    fun sendMessage(text: String, context: Context) {
        if (text.isBlank()) return
        
        // Add user message
        val userMessage = Message(text, true)
        messages = messages + userMessage
        saveMessages()

        // Use pending time or try to parse
        val timeToSchedule = pendingAlarmTime ?: parseDate(text)
        
        if (timeToSchedule != null) {
            scheduleAlarm(context, timeToSchedule, text)
            val dateString = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timeToSchedule))
            val systemMessage = Message("Recordatorio programado para: $dateString", false, reminderTime = timeToSchedule, reminderContent = text)
            messages = messages + systemMessage
            saveMessages()
            pendingAlarmTime = null // Clear pending alarm
        } else {
            val errorMessage = Message("No entendí la fecha. Usa el botón del calendario o escribe 'mañana a las 09:00'", false)
            messages = messages + errorMessage
            saveMessages()
        }
    }

    private fun parseDate(text: String): Long? {
        val lowerText = text.lowercase()
        val calendar = Calendar.getInstance()
        
        // Basic parsing logic
        // Regex for HH:mm
        val timePattern = Pattern.compile("(\\d{1,2}):(\\d{2})")
        val matcher = timePattern.matcher(lowerText)
        
        if (matcher.find()) {
            val hour = matcher.group(1)?.toIntOrNull() ?: return null
            val minute = matcher.group(2)?.toIntOrNull() ?: return null
            
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)

            if (lowerText.contains("mañana")) {
                calendar.add(Calendar.DAY_OF_YEAR, 1)
            } else if (lowerText.contains("hoy")) {
                // Date is already today
            } else {
                // If no day specified, assume today if time is future, else tomorrow
                 if (calendar.timeInMillis < System.currentTimeMillis()) {
                     calendar.add(Calendar.DAY_OF_YEAR, 1)
                 }
            }
            
            return calendar.timeInMillis
        }
        
        return null
    }

    private fun cancelAlarm(context: Context, timeInMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timeInMillis.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun deleteReminder(context: Context, message: Message) {
        if (message.reminderTime != null) {
            cancelAlarm(context, message.reminderTime)
        }
        messages = messages.filter { it != message }
        saveMessages()
        Toast.makeText(context, "Recordatorio eliminado", Toast.LENGTH_SHORT).show()
    }

    fun updateReminderTime(context: Context, message: Message, newTime: Long) {
        if (message.reminderTime != null) {
            cancelAlarm(context, message.reminderTime)
        }

        val dateString = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(newTime))
        val newText = if (message.reminderContent != null) {
            "Recordatorio programado para: $dateString"
        } else {
            // Try to replace the old date string if it exists, or just append
            // This is a bit hacky for old messages, but safe enough
            "Recordatorio reprogramado para: $dateString"
        }

        val updatedMessage = message.copy(
            text = newText,
            reminderTime = newTime,
            timestamp = System.currentTimeMillis() // Update timestamp to move it to bottom if desired, or keep original
        )

        // Replace message
        messages = messages.map { if (it == message) updatedMessage else it }
        
        // Schedule new alarm
        scheduleAlarm(context, newTime, message.reminderContent ?: "Recordatorio")
        saveMessages()
        Toast.makeText(context, "Recordatorio actualizado", Toast.LENGTH_SHORT).show()
    }

    fun scheduleAlarm(context: Context, timeInMillis: Long, message: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val canSchedule = alarmManager.canScheduleExactAlarms()
            android.util.Log.d("Remember20", "canScheduleExactAlarms: $canSchedule")
            if (!canSchedule) {
                android.util.Log.d("Remember20", "Requesting exact alarm permission")
                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                context.startActivity(intent)
                return
            }
        }

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            putExtra("MESSAGE", message)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            timeInMillis.toInt(), // Unique ID based on time
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            android.util.Log.d("Remember20", "Calling setExactAndAllowWhileIdle for: $timeInMillis")
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
            )
            android.util.Log.d("Remember20", "Alarm scheduled successfully")
            // Toast.makeText(context, "Alarma programada", Toast.LENGTH_SHORT).show() // Removed toast to avoid spam on update
        } catch (e: SecurityException) {
            e.printStackTrace()
            android.util.Log.e("Remember20", "SecurityException: ${e.message}")
            Toast.makeText(context, "Error: Falta permiso de alarma exacta", Toast.LENGTH_LONG).show()
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun CalendarScreen(viewModel: ChatViewModel = viewModel()) {
    val messages = viewModel.messages
    val reminders = messages.filter { it.reminderTime != null }
    val calendar = Calendar.getInstance()
    var selectedDate by remember { mutableStateOf<Calendar?>(null) }
    var showOptionsDialog by remember { mutableStateOf<Message?>(null) }
    val context = LocalContext.current

    if (showOptionsDialog != null) {
        AlertDialog(
            onDismissRequest = { showOptionsDialog = null },
            title = { Text("Opciones de Recordatorio") },
            text = { Text("¿Qué deseas hacer con este recordatorio?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val messageToUpdate = showOptionsDialog!!
                        showOptionsDialog = null
                        
                        // Show Date Picker
                        val currentCal = Calendar.getInstance().apply { timeInMillis = messageToUpdate.reminderTime!! }
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val newCalendar = Calendar.getInstance()
                                        newCalendar.set(year, month, dayOfMonth, hourOfDay, minute, 0)
                                        
                                        if (newCalendar.timeInMillis > System.currentTimeMillis()) {
                                            viewModel.updateReminderTime(context, messageToUpdate, newCalendar.timeInMillis)
                                        } else {
                                            Toast.makeText(context, "Selecciona una fecha futura", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    currentCal.get(Calendar.HOUR_OF_DAY),
                                    currentCal.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            currentCal.get(Calendar.YEAR),
                            currentCal.get(Calendar.MONTH),
                            currentCal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    }
                ) {
                    Text("Cambiar Fecha/Hora")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteReminder(context, showOptionsDialog!!)
                        showOptionsDialog = null
                    }
                ) {
                    Text("Eliminar", color = Color.Red)
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Calendario",
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Simple Month View
        val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

            LazyColumn {
                item {
                    Text(
                        text = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time).capitalize(Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                
                val firstDayOfWeek = calendar.apply { set(Calendar.DAY_OF_MONTH, 1) }.get(Calendar.DAY_OF_WEEK)
                val offset = firstDayOfWeek - 1
                val totalCells = daysInMonth + offset
                val rows = (totalCells / 7) + if (totalCells % 7 > 0) 1 else 0

                item {
                    Column {
                        for (row in 0 until rows) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                for (col in 0 until 7) {
                                    val day = (row * 7 + col) - offset + 1
                                    if (day in 1..daysInMonth) {
                                        val dayCalendar = Calendar.getInstance().apply {
                                            set(Calendar.DAY_OF_MONTH, day)
                                            set(Calendar.HOUR_OF_DAY, 0)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                            set(Calendar.MILLISECOND, 0)
                                        }
                                        
                                        val hasReminder = reminders.any { 
                                            val remCal = Calendar.getInstance().apply { timeInMillis = it.reminderTime!! }
                                            remCal.get(Calendar.YEAR) == currentYear &&
                                            remCal.get(Calendar.MONTH) == currentMonth &&
                                            remCal.get(Calendar.DAY_OF_MONTH) == day
                                        }

                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .background(
                                                    if (selectedDate?.get(Calendar.DAY_OF_MONTH) == day) Color(0xFF4A148C) 
                                                    else if (hasReminder) Color(0xFF7B1FA2) else Color.Transparent,
                                                    shape = RoundedCornerShape(20.dp)
                                                )
                                                .border(1.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                                                .clickable {
                                                    selectedDate = dayCalendar
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(text = day.toString(), color = Color.White)
                                            if (hasReminder) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(6.dp)
                                                        .background(Color.White, shape = RoundedCornerShape(3.dp))
                                                        .align(Alignment.TopEnd)
                                                        .offset(x = (-4).dp, y = 4.dp)
                                                )
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.size(40.dp))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Recordatorios del día:", style = MaterialTheme.typography.titleMedium, color = Color.White)
                }

                if (selectedDate != null) {
                    val dayReminders = reminders.filter {
                        val remCal = Calendar.getInstance().apply { timeInMillis = it.reminderTime!! }
                        remCal.get(Calendar.YEAR) == selectedDate!!.get(Calendar.YEAR) &&
                        remCal.get(Calendar.MONTH) == selectedDate!!.get(Calendar.MONTH) &&
                        remCal.get(Calendar.DAY_OF_MONTH) == selectedDate!!.get(Calendar.DAY_OF_MONTH)
                    }

                    if (dayReminders.isEmpty()) {
                        item { Text("No hay recordatorios para este día.", modifier = Modifier.padding(8.dp), color = Color.Gray) }
                    } else {
                        items(dayReminders) { message ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .combinedClickable(
                                        onClick = {},
                                        onLongClick = { showOptionsDialog = message }
                                    ),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.reminderTime!!)),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = Color(0xFFB39DDB)
                                    )
                                    Text(text = message.reminderContent ?: message.text.replace("Recordatorio programado para: ", ""), color = Color.White)
                                }
                            }
                        }
                    }
                } else {
                    item { Text("Selecciona un día para ver los recordatorios.", modifier = Modifier.padding(8.dp), color = Color.Gray) }
                }
            }
    }
}

@Composable
fun ChatScreen(modifier: Modifier = Modifier, viewModel: ChatViewModel = viewModel()) {
    var inputText by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).padding(8.dp),
            reverseLayout = true
        ) {
            items(viewModel.messages.reversed()) { message ->
                MessageBubble(message)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                TextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Escribe un recordatorio...", color = Color.Gray) },
                    shape = RoundedCornerShape(24.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF1A1A1A),
                        unfocusedContainerColor = Color(0xFF1A1A1A),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        val calendar = Calendar.getInstance()
                        DatePickerDialog(
                            context,
                            { _, year, month, dayOfMonth ->
                                TimePickerDialog(
                                    context,
                                    { _, hourOfDay, minute ->
                                        val selectedCalendar = Calendar.getInstance()
                                        selectedCalendar.set(year, month, dayOfMonth, hourOfDay, minute, 0)
                                        
                                        if (selectedCalendar.timeInMillis > System.currentTimeMillis()) {
                                            viewModel.setPendingAlarm(selectedCalendar.timeInMillis)
                                            Toast.makeText(context, "Hora guardada. Escribe el mensaje y envía.", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "Selecciona una fecha futura", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    calendar.get(Calendar.HOUR_OF_DAY),
                                    calendar.get(Calendar.MINUTE),
                                    true
                                ).show()
                            },
                            calendar.get(Calendar.YEAR),
                            calendar.get(Calendar.MONTH),
                            calendar.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier.background(
                        if (viewModel.pendingAlarmTime != null) Color(0xFF4A148C) else Color.Gray, 
                        shape = RoundedCornerShape(50)
                    )
                ) {
                    Icon(Icons.Default.DateRange, contentDescription = "Set Alarm", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        viewModel.sendMessage(inputText, context)
                        inputText = ""
                    },
                    modifier = Modifier.background(Color(0xFF4A148C), shape = RoundedCornerShape(50))
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send", tint = Color.White)
                }
        }
    }
}

@Composable
fun MessageBubble(message: Message) {
    val alignment = if (message.isUser) Alignment.CenterEnd else Alignment.CenterStart
    val backgroundColor = if (message.isUser) Color(0xFF4A148C) else Color(0xFF121212)
    
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = alignment
    ) {
        Card(
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = backgroundColor),
            modifier = Modifier.padding(4.dp).widthIn(max = 280.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(text = message.text, color = Color.White)
                Text(
                    text = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.LightGray,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}