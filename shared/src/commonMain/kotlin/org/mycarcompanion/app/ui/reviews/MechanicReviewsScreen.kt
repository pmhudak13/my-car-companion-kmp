package org.mycarcompanion.app.ui.reviews

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.screen.Screen
import cafe.adriel.voyager.koin.koinScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import org.mycarcompanion.app.data.models.MechanicReview
import org.mycarcompanion.app.platform.CommonParcelable

data class MechanicReviewsScreen(
    val mechanicUserId: String,
    val mechanicName: String,
    val googlePlaceUrl: String? = null,
    val yelpUrl: String? = null,
) : Screen, CommonParcelable {

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val model: MechanicReviewsScreenModel = koinScreenModel()
        val state by model.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }
        val uriHandler = LocalUriHandler.current

        LaunchedEffect(mechanicUserId) { model.load(mechanicUserId) }

        LaunchedEffect(state.submitSuccess) {
            if (state.submitSuccess) {
                snackbarHostState.showSnackbar("Review submitted!")
                model.clearSubmitSuccess()
            }
        }

        if (state.showReviewDialog) {
            AlertDialog(
                onDismissRequest = model::closeReviewDialog,
                title = { Text(if (state.myReview != null) "Edit Your Review" else "Leave a Review") },
                text = {
                    Column {
                        Text("Rating", style = MaterialTheme.typography.labelMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        StarRatingSelector(
                            rating = state.draftRating,
                            onRatingChange = model::setDraftRating,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = state.draftComment,
                            onValueChange = model::setDraftComment,
                            label = { Text("Comment (optional)") },
                            minLines = 3,
                            maxLines = 5,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        state.submitError?.let {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                confirmButton = {
                    if (state.submitting) {
                        CircularProgressIndicator(strokeWidth = 2.dp)
                    } else {
                        Button(onClick = { model.submitReview(mechanicUserId) }) {
                            Text("Submit")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = model::closeReviewDialog) { Text("Cancel") }
                },
            )
        }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Reviews — $mechanicName") },
                    navigationIcon = {
                        IconButton(onClick = { navigator.pop() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        TextButton(onClick = model::openReviewDialog) {
                            Text(if (state.myReview != null) "Edit Review" else "Write Review")
                        }
                    },
                )
            },
        ) { paddingValues ->
            when {
                state.loading -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null -> Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error ?: "Error", color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        TextButton(onClick = { model.load(mechanicUserId) }) { Text("Retry") }
                    }
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Average rating summary
                    if (state.reviews.isNotEmpty()) {
                        item {
                            AverageRatingCard(reviews = state.reviews)
                        }
                    }

                    // External review links
                    if (!googlePlaceUrl.isNullOrBlank() || !yelpUrl.isNullOrBlank()) {
                        item {
                            ExternalReviewLinks(
                                googlePlaceUrl = googlePlaceUrl,
                                yelpUrl = yelpUrl,
                                onOpenUrl = { uriHandler.openUri(it) },
                            )
                        }
                    }

                    if (state.reviews.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "No reviews yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Button(onClick = model::openReviewDialog) { Text("Be the first to review") }
                                }
                            }
                        }
                    } else {
                        items(state.reviews, key = { it.id }) { review ->
                            ReviewCard(review = review, isOwn = review.id == state.myReview?.id)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AverageRatingCard(reviews: List<MechanicReview>) {
    val avg = reviews.map { it.rating }.average()
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "%.1f out of 5".format(avg),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = "${reviews.size} review${if (reviews.size != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            StarRatingDisplay(rating = avg.toFloat(), maxStars = 5)
        }
    }
}

@Composable
private fun ExternalReviewLinks(
    googlePlaceUrl: String?,
    yelpUrl: String?,
    onOpenUrl: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("External Reviews", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!googlePlaceUrl.isNullOrBlank()) {
                    OutlinedButton(onClick = { onOpenUrl(googlePlaceUrl) }) {
                        Text("View on Google")
                    }
                }
                if (!yelpUrl.isNullOrBlank()) {
                    OutlinedButton(onClick = { onOpenUrl(yelpUrl) }) {
                        Text("View on Yelp")
                    }
                }
            }
        }
    }
}

@Composable
fun StarRatingDisplay(rating: Float, maxStars: Int = 5, starSize: Int = 24) {
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..maxStars).forEach { star ->
            Icon(
                imageVector = if (star <= rating.toInt()) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = null,
                tint = if (star <= rating.toInt())
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(starSize.dp),
            )
        }
    }
}

@Composable
fun StarRatingSelector(rating: Int, onRatingChange: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..5).forEach { star ->
            IconToggleButton(
                checked = star <= rating,
                onCheckedChange = { onRatingChange(star) },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    imageVector = if (star <= rating) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = "$star star${if (star != 1) "s" else ""}",
                    tint = if (star <= rating)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun ReviewCard(review: MechanicReview, isOwn: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOwn)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StarRatingDisplay(rating = review.rating.toFloat())
                if (isOwn) {
                    Text(
                        "Your review",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Text(
                        review.createdAt.take(10),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            review.comment?.let { comment ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(comment, style = MaterialTheme.typography.bodyMedium)
            }
            if (isOwn) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    review.createdAt.take(10),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
