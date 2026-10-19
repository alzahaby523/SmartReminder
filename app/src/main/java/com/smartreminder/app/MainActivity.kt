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
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

    // تحديث القائمة دورياً لتعكس التذكيرات التي تم تنبيهها والوقت المتبقي
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

// ---------------------------------------------------------------------------
// شاشة القائمة
// ---------------------------------------------------------------------------

private fun startOfDay(ms: Long): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = ms
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}

private fun dayLabel(ms: Long): String {
    val diff = Math.round((startOfDay(ms) - startOfDay(System.currentTimeMillis())) / 86_400_000.0).toInt()
    return when (diff) {
        0 -> "اليوم"
        1 -> "غداً"
        -1 -> "أمس"
        else -> SimpleDateFormat("EEEE d MMMM", Locale.forLanguageTag("ar-u-nu-latn")).format(Date(ms))
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
    val next = upcoming.firstOrNull()

    // صفوف القائمة: نص = عنوان يوم، Reminder = عنصر
    val rows = ArrayList<Any>()
    var lastDay = ""
    upcoming.drop(1).forEach {
        val d = dayLabel(it.eventAt)
        if (d != lastDay) {
            rows.add(d)
            lastDay = d
        }
        rows.add(it)
    }
    if (past.isNotEmpty()) {
        rows.add("السابقة")
        past.reversed().forEach { rows.add(it) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "تذكير جديد")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("تذكير ذكي", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                OutlinedButton(
                    onClick = onTest,
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("تجربة", fontSize = 13.sp)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 110.dp)
            ) {
                item {
                    if (next != null) {
                        HeroCard(next, now) { onEdit(next) }
                    } else {
                        EmptyHero(reminders.isEmpty())
                    }
                    Spacer(Modifier.height(18.dp))
                }
                items(rows) { row ->
                    if (row is String) {
                        Text(
                            row,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)
                        )
                    } else if (row is Reminder) {
                        TimelineItem(row, dim = row.eventAt < now, onEdit = onEdit, onDelete = onDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(r: Reminder, now: Long, onClick: () -> Unit) {
    val isAppointment = r.type == Reminder.TYPE_APPOINTMENT
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(Brush.linearGradient(listOf(HeroStart, HeroEnd)))
            .clickable { onClick() }
            .padding(20.dp)
    ) {
        Column {
            Text("التالي", fontSize = 12.sp, color = Color.White.copy(alpha = 0.75f))
            Spacer(Modifier.height(6.dp))
            Text(
                ArabicTime.relative(r.eventAt, now),
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White
            )
            Text(
                if (isAppointment) "موعد مع ${r.title}" else r.title,
                fontSize = 15.sp,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 2
            )
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeroTag("الساعة " + Fmt.time(r.eventAt))
                HeroTag(if (r.voice) "صوتي" else "صوري فقط")
            }
        }
    }
}

@Composable
private fun HeroTag(text: String) {
    Surface(color = Color.White.copy(alpha = 0.16f), shape = RoundedCornerShape(14.dp)) {
        Text(
            text,
            fontSize = 12.sp,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun EmptyHero(noReminders: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(26.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (noReminders) "لا توجد تذكيرات بعد.\nاضغط + لإضافة موعد أو عمل،\nأو جرّب «تجربة» لتسمع التنبيه الذكي."
            else "لا توجد تذكيرات قادمة",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun TimelineItem(
    r: Reminder,
    dim: Boolean,
    onEdit: (Reminder) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    val isAppointment = r.type == Reminder.TYPE_APPOINTMENT
    Column(modifier = Modifier.alpha(if (dim) 0.5f else 1f)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onEdit(r) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                Fmt.time(r.eventAt),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.width(60.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isAppointment) "موعد" else "عمل",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(r.title, fontSize = 16.sp, maxLines = 2)
                Text(
                    (if (r.voice) "صوتي" else "صوري فقط") + " · " + Fmt.leadLabel(r.leadMin),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = { onDelete(r) }) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "حذف",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// ---------------------------------------------------------------------------
// شاشة الإضافة / التعديل
// ---------------------------------------------------------------------------

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
private fun SectionLabel(text: String) {
    Text(
        text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** مفتاح اختيار من عدة خيارات (مثل: موعد / عمل، صوتي / صوري فقط). */
@Composable
private fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (sel) MaterialTheme.colorScheme.surface else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.Medium else FontWeight.Normal,
                    color = if (sel) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
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
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                }
                Text(
                    if (initial == null) "تذكير جديد" else "تعديل التذكير",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick = { trySave() },
                    shape = RoundedCornerShape(27.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    Text("حفظ التذكير", fontSize = 16.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Segmented(
                options = listOf("موعد", "عمل"),
                selected = type,
                onSelect = { type = it }
            )

            Column {
                SectionLabel(if (type == Reminder.TYPE_APPOINTMENT) "مع من؟" else "ما هو العمل؟")
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text(if (type == Reminder.TYPE_APPOINTMENT) "شخص أو شركة" else "مثال: مراجعة التقرير")
                    },
                    textStyle = MaterialTheme.typography.titleLarge,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("وقت الموعد")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { pickDate() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text(Fmt.dateShort(eventAt), fontSize = 14.sp) }
                    OutlinedButton(
                        onClick = { pickTime() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).height(48.dp)
                    ) { Text(Fmt.time(eventAt), fontSize = 14.sp) }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SectionLabel("متى أنبّهك؟")
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LEAD_OPTIONS.forEach { option ->
                        FilterChip(
                            selected = lead == option,
                            onClick = { lead = option },
                            label = { Text(Fmt.leadLabel(option)) },
                            shape = RoundedCornerShape(20.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("أسلوب التنبيه")
                Segmented(
                    options = listOf("صوتي", "صوري فقط"),
                    selected = if (voice) 0 else 1,
                    onSelect = { voice = it == 0 }
                )
                Text(
                    if (voice)
                        "الهاتف مفتوح: إشعار منبثق.\nالهاتف مقفل: جرس مرتين ثم يُنطق التذكير."
                    else
                        "إشعار منبثق واهتزاز فقط، بدون جرس أو صوت.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (voice && title.isNotBlank()) {
                    val rel = if (lead <= 0) "الآن" else "بعد " + ArabicTime.duration(lead.toLong())
                    val preview = Reminder(0L, title.trim(), type, eventAt, lead, true)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            "«" + Speech.spoken(preview, rel) + "»",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(14.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
