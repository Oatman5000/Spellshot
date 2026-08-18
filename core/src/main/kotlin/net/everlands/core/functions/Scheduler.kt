package net.everlands.core.functions

import com.hypixel.hytale.server.core.HytaleServer
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


fun schedule(delayInMillis: Long, block: () -> Unit): ScheduleTask {
    val future = HytaleServer.SCHEDULED_EXECUTOR.schedule(block, delayInMillis, TimeUnit.MILLISECONDS)
    return ScheduleTask(future)
}

fun schedule(delayInMillis: Long, repeatInMillis: Long, block: (task: ScheduleTask) -> Unit): ScheduleTask {
    val task = ScheduleTask()
    val future = HytaleServer.SCHEDULED_EXECUTOR.scheduleAtFixedRate(
        { block(task) },
        delayInMillis,
        repeatInMillis,
        TimeUnit.MILLISECONDS
    )
    task.scheduleFuture = future
    return task
}

data class ScheduleTask(
    var scheduleFuture: ScheduledFuture<*>? = null
) {
    fun cancel() {
        scheduleFuture?.cancel(false)
    }
}
