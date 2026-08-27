package dev.pgm.roadmate.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Whether the user has acknowledged the first-run intro (value prop + the
 * "no reemplaza tu atención a la carretera" driving-safety disclaimer).
 */
interface OnboardingRepository {
    val isOnboardingCompleted: Flow<Boolean>
    suspend fun setOnboardingCompleted()
}
