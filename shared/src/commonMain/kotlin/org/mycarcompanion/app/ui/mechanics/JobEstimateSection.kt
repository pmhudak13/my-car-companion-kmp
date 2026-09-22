package org.mycarcompanion.app.ui.mechanics

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mycarcompanion.app.data.models.JobLineItem
import org.mycarcompanion.app.data.models.MechanicJob
import org.mycarcompanion.app.data.models.MechanicJobIssue
import org.mycarcompanion.app.data.models.authorizedTotal
import org.mycarcompanion.app.data.models.stage
import org.mycarcompanion.app.ui.formatMoney

private val lineKinds = listOf("labor" to "Labor", "part" to "Part", "fee" to "Fee")

/** Mechanic view: line items, running total, and the customer's approval record. */
@Composable
fun EstimateCard(
    job: MechanicJob,
    lineItems: List<JobLineItem>,
    issues: List<MechanicJobIssue>,
    form: LineItemForm,
    isSaving: Boolean,
    error: String?,
    isApproving: Boolean,
    onFormChange: (LineItemForm) -> Unit,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onApprove: () -> Unit,
) {
    val editable = job.status == "open"
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Estimate", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(job.stage, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            LineItemList(lineItems, onDelete = onDelete.takeIf { editable })

            if (editable) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    lineKinds.forEach { (kind, label) ->
                        FilterChip(
                            selected = form.kind == kind,
                            onClick = { onFormChange(form.copy(kind = kind)) },
                            label = { Text(label) },
                        )
                    }
                }
                OutlinedTextField(
                    value = form.description,
                    onValueChange = { onFormChange(form.copy(description = it)) },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = form.quantity,
                        onValueChange = { onFormChange(form.copy(quantity = it)) },
                        label = { Text(if (form.kind == "labor") "Hours" else "Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = form.unitPrice,
                        onValueChange = { onFormChange(form.copy(unitPrice = it)) },
                        label = { Text(if (form.kind == "labor") "Rate" else "Price") },
                        leadingIcon = { Text("$") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    if (isSaving) CircularProgressIndicator(Modifier.size(24.dp))
                    else FilledTonalButton(onClick = onAdd) { Text("Add") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }

            ApprovalStatus(job, issues)
            if (editable && job.totalCost != null && needsApproval(job, issues)) {
                Spacer(Modifier.height(8.dp))
                if (isApproving) CircularProgressIndicator(Modifier.size(24.dp))
                else Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
                    Text("Customer approved $${formatMoney(job.totalCost)}")
                }
                Text(
                    "Tap once the customer OKs this amount in person or by phone. Linked owners can approve in their app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Owner view: what the mechanic quoted, plus an approve button while it's awaiting approval. */
@Composable
fun OwnerEstimateSection(
    job: MechanicJob,
    lineItems: List<JobLineItem>,
    issues: List<MechanicJobIssue>,
    isApproving: Boolean,
    onApprove: () -> Unit,
) {
    if (lineItems.isEmpty() && job.totalCost == null) return
    Column {
        Text("Estimate · ${job.stage}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        LineItemList(lineItems, onDelete = null)
        ApprovalStatus(job, issues)
        if (job.status == "open" && job.totalCost != null && needsApproval(job, issues)) {
            Spacer(Modifier.height(4.dp))
            if (isApproving) CircularProgressIndicator(Modifier.size(24.dp))
            else Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
                Text("Approve $${formatMoney(job.totalCost)}")
            }
        }
    }
}

/** Mitchell "Recommendations": work the customer declined on earlier visits to this car. */
@Composable
fun PreviouslyDeclinedCard(issues: List<MechanicJobIssue>, onReflag: (MechanicJobIssue) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "Declined on earlier visits (${issues.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            issues.forEach { issue ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(issue.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(
                            listOfNotNull(
                                issue.createdAt.take(10),
                                issue.estimatedCost?.let { "$${formatMoney(it)}" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(onClick = { onReflag(issue) }) { Text("Re-flag") }
                }
            }
        }
    }
}

@Composable
private fun LineItemList(items: List<JobLineItem>, onDelete: ((String) -> Unit)?) {
    if (items.isEmpty()) {
        Text("No line items yet.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    items.forEach { item ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(item.description, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "${item.kind.replaceFirstChar { it.uppercase() }} · ${formatQty(item.quantity)} × $${formatMoney(item.unitPrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("$${formatMoney(item.lineTotal)}", style = MaterialTheme.typography.bodyMedium)
            if (onDelete != null) {
                IconButton(onClick = { onDelete(item.id) }) { Icon(Icons.Default.Delete, contentDescription = "Remove line") }
            }
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Total", fontWeight = FontWeight.Bold)
        Text("$${formatMoney(items.sumOf { it.lineTotal })}", fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ApprovalStatus(job: MechanicJob, issues: List<MechanicJobIssue>) {
    val approvedAt = job.estimateApprovedAt ?: return
    val authorized = job.authorizedTotal(issues)
    Spacer(Modifier.height(4.dp))
    Text(
        "Approved $${formatMoney(authorized)} on ${approvedAt.take(10)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (needsApproval(job, issues)) {
        Text(
            "Total is now over the approved amount. Get approval before doing the extra work.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

// ponytail: 1-cent tolerance for float sums; switch money to Long cents if rounding ever bites
private fun needsApproval(job: MechanicJob, issues: List<MechanicJobIssue>): Boolean =
    job.estimateApprovedAt == null || (job.totalCost ?: 0.0) > job.authorizedTotal(issues) + 0.01

private fun formatQty(q: Double): String = if (q % 1.0 == 0.0) q.toLong().toString() else q.toString()
