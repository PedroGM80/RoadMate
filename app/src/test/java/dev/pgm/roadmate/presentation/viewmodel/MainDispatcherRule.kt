package dev.pgm.roadmate.presentation.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps Dispatchers.Main for a TestDispatcher so viewModelScope launches run
 * deterministically instead of needing a real Android main-thread Looper
 * (unavailable in a plain JUnit unit test).
 *
 * Exposes [testDispatcher] so tests can pass it into runTest(testDispatcher) {}
 * — runTest has its own TestCoroutineScheduler by default, separate from
 * whatever Dispatchers.Main was swapped to here; without sharing the same
 * dispatcher/scheduler between the two, coroutines launched via
 * viewModelScope (which runs on Dispatchers.Main) never get driven to
 * completion by runTest's virtual-time advancement.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val testDispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {

    override fun starting(description: Description) {
        kotlinx.coroutines.Dispatchers.setMain(testDispatcher)
    }

    override fun finished(description: Description) {
        kotlinx.coroutines.Dispatchers.resetMain()
    }
}
