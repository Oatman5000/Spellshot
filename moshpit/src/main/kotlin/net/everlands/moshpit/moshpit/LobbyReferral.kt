package net.everlands.moshpit.moshpit

import io.github.smiley4.schemakenerator.core.annotations.Description
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.everlands.core.api.EverlandsLeave
import net.everlands.core.framework.Config
import net.everlands.core.framework.OnStart
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.stereotype.Service

/**
 * Where `/lobby` sends players from the game server.
 *
 * Ships blank, which makes `/lobby` answer "standalone" rather than referring
 * anyone anywhere. It used to default to a specific public server, which meant a
 * fresh clone of this repo shipped players to somebody else's instance.
 *
 * Point it at your lobby with an environment variable rather than a rebuild:
 *
 *   EVERLANDS_LOBBY_HOST   overrides host
 *   EVERLANDS_LOBBY_PORT   overrides port
 */
@Serializable
@SerialName("minigame.LobbyReferralConfig")
data class LobbyReferralConfig(
    @Description("Lobby server host. Blank disables /lobby referral. Prefer EVERLANDS_LOBBY_HOST.")
    val host: String = "",

    @Description("Lobby server port.")
    val port: Int = 5523,
) {
    val effectiveHost: String get() = env("EVERLANDS_LOBBY_HOST") ?: host
    val effectivePort: Int get() = env("EVERLANDS_LOBBY_PORT")?.toIntOrNull() ?: port

    private fun env(name: String): String? = System.getenv(name)?.takeIf { it.isNotBlank() }

    @Component
    class Binder : Config<LobbyReferralConfig>(LobbyReferralConfig())
}

/**
 * Points [EverlandsLeave] at the lobby.
 *
 * EverlandsLeave is an object rather than a bean because it is called statically
 * from LeaveCommand and MoshpitManager, so its target is pushed in here at
 * startup instead of injected.
 */
@Service
class LobbyReferral(val config: LobbyReferralConfig.Binder) {

    private val log = LoggerFactory.getLogger(javaClass)

    @OnStart
    fun start() {
        val cfg = config.value
        val host = cfg.effectiveHost
        if (host.isBlank()) {
            log.info("/lobby referral disabled - set EVERLANDS_LOBBY_HOST to enable it")
            return
        }
        EverlandsLeave.target = EverlandsLeave.Target(host, cfg.effectivePort)
        log.info("/lobby will refer players to {}:{}", host, cfg.effectivePort)
    }
}
