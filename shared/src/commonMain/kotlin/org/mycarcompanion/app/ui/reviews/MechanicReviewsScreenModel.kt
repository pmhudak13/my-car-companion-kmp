package org.mycarcompanion.app.ui.reviews

import cafe.adriel.voyager.core.model.ScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.mycarcompanion.app.data.models.MechanicReview
import org.mycarcompanion.app.data.repository.ReviewRepository

data class MechanicReviewsState(
    val reviews: List<MechanicReview> = emptyList(),
    val myReview: MechanicReview? = null,
    val loading: Boolean = true,
    val submitting: Boolean = false,
    val error: String? = null,
    val submitError: String? = null,
    val showReviewDialog: Boolean = false,
    val draftRating: Int = 5,
    val draftComment: String = "",
    val submitSuccess: Boolean = false,
)

class MechanicReviewsScreenModel(
    private val reviewRepository: ReviewRepository,
) : ScreenModel {

    private val _state = MutableStateFlow(MechanicReviewsState())
    val state: StateFlow<MechanicReviewsState> = _state.asStateFlow()

    fun load(mechanicUserId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val reviews = reviewRepository.getReviewsForMechanic(mechanicUserId)
                .getOrElse { e ->
                    _state.value = _state.value.copy(loading = false, error = e.message ?: "Failed to load reviews")
                    return@launch
                }
            val myReview = reviewRepository.getMyReviewForMechanic(mechanicUserId).getOrNull()
            _state.value = _state.value.copy(
                loading = false,
                reviews = reviews,
                myReview = myReview,
                draftRating = myReview?.rating ?: 5,
                draftComment = myReview?.comment ?: "",
            )
        }
    }

    fun openReviewDialog() {
        _state.value = _state.value.copy(showReviewDialog = true, submitError = null)
    }

    fun closeReviewDialog() {
        _state.value = _state.value.copy(showReviewDialog = false)
    }

    fun setDraftRating(rating: Int) {
        _state.value = _state.value.copy(draftRating = rating)
    }

    fun setDraftComment(comment: String) {
        _state.value = _state.value.copy(draftComment = comment)
    }

    fun submitReview(mechanicUserId: String) {
        screenModelScope.launch {
            _state.value = _state.value.copy(submitting = true, submitError = null)
            reviewRepository.submitReview(mechanicUserId, _state.value.draftRating, _state.value.draftComment)
                .onSuccess {
                    _state.value = _state.value.copy(submitting = false, showReviewDialog = false, submitSuccess = true)
                    load(mechanicUserId)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        submitting = false,
                        submitError = e.message ?: "Failed to submit review",
                    )
                }
        }
    }

    fun clearSubmitSuccess() {
        _state.value = _state.value.copy(submitSuccess = false)
    }
}
