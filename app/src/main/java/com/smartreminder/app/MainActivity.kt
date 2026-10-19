@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.smartreminder.app

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Notifications.ensureChannel(this)
        setContent {
            SmartReminderTheme {
                AppRoot()
            }
        }
    }
}

private val LEAD_OPTIONS = listOf(0, 5, 10, 15, 30, 60, 120, 1440)

@Composable
fun AppRoot() {
    val ctx = LocalContext.current
    val store = remember { ReminderStore(ctx) }
    var reminders by remember { mutableStateOf(store.all()) }
    var editorOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Reminder?>(null) }

    val permLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // تحديث القائمة دورياً لتعكس التذكيرات التي تم تنبيهها
    LaunchedEffect(Unit) {
        while (true) {
            delay(3_000L)
            reminders = store.all()
        }
    }

    fun closeEditor() {
        editorOpen = false
        editing = null
    }

    fun delete(r: Reminder) {
        AlarmScheduler.cancel(ctx, r.id)
        NotificationManagerCompat.from(ctx).cancel(Notifications.idFor(r.id))
        store.delete(r.id)
        reminders = store.all()
    }

    fun save(r: Reminder) {
        AlarmScheduler.cancel(ctx, r.id)
        store.upsert(r)
        AlarmScheduler.schedule(ctx, r)
        reminders = store.all()
        closeEditor()
    }

    fun test() {
        val now = System.currentTimeMillis()
        // موعد وهمي بعد نصف ساعة، والتنبيه ينطلق بعد 10 ثوان
        val r = Reminder(
            id = now,
            title = "شركة الأمل",
            type = Reminder.TYPE_APPOINTMENT,
            eventAt = now + 30 * 60_000L + 10_000L,
            leadMin = 30,
            voice = true
        )
        store.upsert(r)
        AlarmScheduler.schedule(ctx, r, now + 10_000L)
        reminders = store.all()
        Toast.makeText(
            ctx,
            "سيصلك تنبيه تجريبي بعد 10 ثوانٍ. أقفل الشاشة لتسمع الجرس والصوت",
            Toast.LENGTH_LONG
        ).show()
    }

    BackHandler(enabled = editorOpen) { closeEditor() }

    if (editorOpen) {
        EditorScreen(initial = editing, onClose = { closeEditor() }, onSave = { save(it) })
    } else {
        ListScreen(
            reminders = reminders,
            onAdd = {
                editing = null
                editorOpen = true
            },
            onEdit = {
                editing = it
                editorOpen = true
            },
            onDelete = { delete(it) },
            onTest = { test() }
        )
    }
}

@Composable
fun ListScreen(
    reminders: List<Reminder>,
    onAdd: () -> Unit,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit,
    onTest: () -> Unit
) {
    val now = System.currentTimeMillis()
    val (past, upcoming) = reminders.partition { it.eventAt < now }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("تذكير ذكي", fontWeight = FontWeight.Bold) },
                actions = { TextButton(onClick = onTest) { Text("🔔 تجربة") } }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("تذكير جديد") }
            )
        }
    ) { padding ->
        if (reminders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "لا توجد تذكيرات بعد.\nاضغط «تذكير جديد» لإضافة موعد أو عمل،\nأو جرّب «تجربة» لتسمع التنبيه الذكي.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (upcoming.isNotEmpty()) {
                    item { SectionTitle("القادمة") }
                    items(upcoming, key = { it.id }) { ReminderCard(it, false, onEdit, onDelete) }
                }
                if (past.isNotEmpty()) {
                    item { SectionTitle("السابقة") }
                    items(past.reversed(), key = { it.id }) { ReminderCard(it, true, onEdit, onDelete) }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
    )
}

@Composable
private fun ReminderCard(
    r: Reminder,
    dim: Boolean,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    val isAppointment = r.type == Reminder.TYPE_APPOINTMENT
    Card(
        onClick = { onEdit(r) },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (dim) 0.6f else 1f)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(if (isAppointment) "📅" else "✅", fontSize = 28.sp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isAppointment) "موعد مع ${r.title}" else r.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                )
                Text(
                    text = Fmt.dateTime(r.eventAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = Fmt.leadLabel(r.leadMin) + "  ·  " + if (r.voice) "🔊 صوتي" else "👁 صوري فقط",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { onDelete(r) }) {
                Icon(Icons.Default.Delete, contentDescription = "حذف")
            }
        }
    }
}

private fun defaultEventTime(): Long {
    val c = Calendar.getInstance()
    c.add(Calendar.MINUTE, 60)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    val m = c.get(Calendar.MINUTE)
    c.add(Calendar.MINUTE, (5 - m % 5) % 5)
    return c.timeInMillis
}

@Composable
fun EditorScreen(initial: Reminder?, onClose: () -> Unit, onSave: (Reminder) -> Unit) {
    val ctx = LocalContext.current
    var type by remember { mutableIntStateOf(initial?.type ?: Reminder.TYPE_APPOINTMENT) }
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var eventAt by remember { mutableLongStateOf(initial?.eventAt ?: defaultEventTime()) }
    var lead by remember { mutableIntStateOf(initial?.leadMin ?: 30) }
    var voice by remember { mutableStateOf(initial?.voice ?: true) }

    fun pickDate() {
        val c = Calendar.getInstance().apply { timeInMillis = eventAt }
        DatePickerDialog(
            ctx,
            { _, y, m, d ->
                val n = Calendar.getInstance().apply {
                    timeInMillis = eventAt
                    set(y, m, d)
                }
                eventAt = n.timeInMillis
            },
            c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    fun pickTime() {
        val c = Calendar.getInstance().apply { timeInMillis = eventAt }
        TimePickerDialog(
            ctx,
            { _, h, min ->
                val n = Calendar.getInstance().apply {
                    timeInMillis = eventAt
                    set(Calendar.HOUR_OF_DAY, h)
                    set(Calendar.MINUTE, min)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                eventAt = n.timeInMillis
            },
            c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true
        ).show()
    }

    fun trySave() {
        val t = title.trim()
        when {
            t.isEmpty() ->
                Toast.makeText(ctx, "اكتب اسم الشخص أو الجهة أو العمل", Toast.LENGTH_SHORT).show()
            eventAt <= System.currentTimeMillis() ->
                Toast.makeText(ctx, "اختر وقتاً في المستقبل", Toast.LENGTH_SHORT).show()
            else -> onSave(
                Reminder(
                    id = initial?.id ?: System.currentTimeMillis(),
                    title = t,
                    type = type,
                    eventAt = eventAt,
                    leadMin = lead,
                    voice = voice
                )
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (initial == null) "تذكير جديد" else "تعديل التذكير") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("نوع التذكير", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = type == Reminder.TYPE_APPOINTMENT,
                    onClick = { type = Reminder.TYPE_APPOINTMENT },
                    label = { Text("📅 موعد") }
                )
                FilterChip(
                    selected = type == Reminder.TYPE_TASK,
                    onClick = { type = Reminder.TYPE_TASK },
                    label = { Text("✅ عمل") }
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = {
                    Text(
                        if (type == Reminder.TYPE_APPOINTMENT) "مع من؟ (شخص أو شركة)"
                        else "ما هو العمل؟"
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Text("وقت الموعد", fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate() }, modifier = Modifier.weight(1f)) {
                    Text("📆 " + Fmt.dateShort(eventAt))
                }
                OutlinedButton(onClick = { pickTime() }, modifier = Modifier.weight(1f)) {
                    Text("🕒 " + Fmt.time(eventAt))
                }
            }

            Text("متى أنبّهك؟", fontWeight = FontWeight.Bold)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LEAD_OPTIONS.forEach { option ->
                    FilterChip(
                        selected = lead == option,
                        onClick = { lead = option },
                        label = { Text(Fmt.leadLabel(option)) }
                    )
                }
            }

            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (voice) "🔊 تنبيه صوتي" else "👁 صوري فقط",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (voice)
                                    "الهاتف مفتوح: إشعار منبثق.\nالهاتف مقفل: جرس مرتين ثم يُنطق التذكير."
                                else
                                    "إشعار منبثق واهتزاز فقط، بدون جرس أو صوت.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = voice, onCheckedChange = { voice = it })
                    }
                    if (voice && title.isNotBlank()) {
                        val rel = if (lead <= 0) "الآن" else "بعد " + ArabicTime.duration(lead.toLong())
                        val preview = Reminder(0L, title.trim(), type, eventAt, lead, true)
                        Text(
                            "سيُقال: «" + Speech.spoken(preview, rel) + "»",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { trySave() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text("حفظ التذكير", fontSize = 16.sp)
            }
        }
    }
}
