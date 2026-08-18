package net.everlands.core.ext

import org.springframework.context.PayloadApplicationEvent
import org.springframework.context.event.ApplicationEventMulticaster

inline fun <reified T> ApplicationEventMulticaster.subscribe(crossinline handle: (event: T) -> Unit) {
    addApplicationListener { event ->
        if (event is PayloadApplicationEvent<*>) {
            if(event.payload is T) {
                handle(event.payload as T)
            }
        }
    }
}
