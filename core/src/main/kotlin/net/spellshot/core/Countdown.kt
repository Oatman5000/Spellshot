package net.spellshot.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

abstract class Countdown(val maxSeconds: Int) {
    /** Overridden by world-bound countdowns so ticks resume on the world thread. */
    protected open val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)

    val isRunning: Boolean
        get() = job?.isActive == true

    val remainingSeconds: Int
        get() {
            if (!isRunning) return maxSeconds
            val remainingMillis = endTimeInMillis - System.currentTimeMillis()
            return if (remainingMillis <= 0) 0 else (remainingMillis / 1000).toInt()
        }

    private var job: Job? = null

    private var endTimeInMillis = 0L

    fun start() {
        job?.cancel()
        reset()
        job = scope.launch {
            onStart()
            while (isActive) {
                val remainingMillis = endTimeInMillis - System.currentTimeMillis()
                if (remainingMillis <= 0) break
                onTick((remainingMillis / 1000).toInt())
                delay(1_000)
            }
            onFinish()
        }
    }

    fun stop() {
        job?.cancel()
        onStop()
        job = null
    }

    fun reset() {
        endTimeInMillis = System.currentTimeMillis() + maxSeconds * 1000L
    }

    abstract suspend fun onTick(remainingSeconds: Int)
    abstract suspend fun onFinish()
    open suspend fun onStart() {}
    open fun onStop() {}
}
