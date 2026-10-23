package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.mycarcompanion.app.data.models.MechanicJob
import kotlin.math.ceil

/** Seconds on the job's clock: banked time plus the running stretch, if any. */
fun timerSeconds(job: MechanicJob, now: Instant): Long {
    val running = job.timerStartedAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        ?.let { (now - it).inWholeSeconds.coerceAtLeast(0) } ?: 0
    return job.timerLoggedSeconds + running
}

/** Billed hours, rounded up to the next tenth like a flat-rate shop (0 stays 0). */
fun billedHours(seconds: Long): Double = ceil(seconds / 360.0) / 10

private fun clock(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return "$h:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}"
}

@Composable
fun JobTimerCard(
    job: MechanicJob,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onReset: () -> Unit,
    onUseAsLabor: (hours: Double) -> Unit,
) {
    var now by remember { mutableStateOf(Clock.System.now()) }
    LaunchedEffect(job.timerStartedAt) {
        now = Clock.System.now()
        while (job.timerStartedAt != null) {
            delay(1_000)
            now = Clock.System.now()
        }
    }
    val seconds = timerSeconds(job, now)
    val hours = billedHours(seconds)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Job Timer", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(clock(seconds), style = MaterialTheme.typography.headlineSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (job.timerStartedAt == null) Button(onClick = onStart) { Text(if (seconds == 0L) "Start" else "Resume") }
                else OutlinedButton(onClick = onStop) { Text("Pause") }
                if (seconds > 0 && job.timerStartedAt == null) TextButton(onClick = onReset) { Text("Reset") }
            }
            if (hours > 0 && job.timerStartedAt == null) {
                TextButton(onClick = { onUseAsLabor(hours) }) { Text("Use ${formatQty(hours)} h on a labor line") }
            }
        }
    }
}
