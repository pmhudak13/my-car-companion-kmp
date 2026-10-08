package org.mycarcompanion.app.ui.mechanics

import org.mycarcompanion.app.data.models.JobLineItem
import org.mycarcompanion.app.data.models.MechanicJob

/** A finished or imported job the mechanic can copy lines from. */
data class PastJob(val job: MechanicJob, val lines: List<JobLineItem>) {
    val date: String get() = (job.completedAt ?: job.createdAt).take(10)
    val total: Double get() = job.totalCost ?: lines.sumOf { it.lineTotal }
}

/** Jobs other than [currentJobId] that have lines, newest first. [history] is the mechanic's lines. */
fun pastJobs(jobs: List<MechanicJob>, history: List<JobLineItem>, currentJobId: String): List<PastJob> {
    val linesByJob = history.groupBy { it.mechanicJobId }
    return jobs.filter { it.id != currentJobId }
        .mapNotNull { job -> linesByJob[job.id]?.let { PastJob(job, it.sortedBy(JobLineItem::createdAt)) } }
        .sortedByDescending { it.date }
}

/**
 * What the mechanic charged last time for a line like the one being typed: same kind, exact name
 * first, then a name containing what's typed. [history] must be newest first.
 */
fun List<JobLineItem>.lastCharged(kind: String, description: String): JobLineItem? {
    val typed = description.trim().lowercase()
    if (typed.length < 3) return null
    val sameKind = filter { it.kind == kind }
    return sameKind.firstOrNull { it.description.trim().lowercase() == typed }
        ?: sameKind.firstOrNull { typed in it.description.lowercase() }
}
