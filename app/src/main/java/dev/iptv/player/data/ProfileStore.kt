package dev.iptv.player.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class Profile(val id: String, val name: String, val color: Int = 0, val pin: String = "")

/**
 * Process-wide active profile + key namespacing.
 *
 * Anything that is stored per viewer (favorites, watch positions, watch history...) must derive its
 * storage key through [profileScopedKey]. The default profile maps to the key unchanged, so data
 * written before profiles existed stays visible to the default profile.
 */
object ProfileStore {
    const val DEFAULT_ID = "default"
    val avatarColors = listOf(0xFF5B8CFF, 0xFF34D1BF, 0xFFFF4D5E, 0xFFFFC857, 0xFFB57BFF, 0xFFFF8A4C, 0xFF6BCB77, 0xFFEC6FAE)

    private val active = MutableStateFlow(DEFAULT_ID)
    val activeIdFlow: StateFlow<String> = active

    /** Id of the profile currently in use. */
    val activeProfileId: String get() = active.value

    /** Set after the picker ran (or was unnecessary) so it is shown once per launch. */
    @Volatile var pickedThisLaunch = false

    fun setActive(id: String) { active.value = id }

    /** Namespaces [key] for [profileId] (default: the active profile). Default profile = unchanged key. */
    fun profileScopedKey(key: String, profileId: String = activeProfileId): String =
        if (profileId == DEFAULT_ID) key else "profile.$profileId.$key"

    private val json = Json { ignoreUnknownKeys = true }

    fun decode(raw: String): List<Profile> {
        val stored = runCatching { json.decodeFromString<List<Profile>>(raw) }.getOrDefault(emptyList())
        val default = stored.firstOrNull { it.id == DEFAULT_ID } ?: Profile(DEFAULT_ID, "Ana profil", 0)
        return listOf(default) + stored.filter { it.id != DEFAULT_ID }
    }

    fun encode(profiles: List<Profile>): String = json.encodeToString(profiles)

    fun newProfile(name: String, color: Int) = Profile(UUID.randomUUID().toString().take(8), name, color)
}

/** Persistence for the profile list on top of [SettingsStore]. */
class ProfileRepository(private val settings: SettingsStore) {
    val profiles: Flow<List<Profile>> = settings.flow(Setting.Profiles).map { ProfileStore.decode(it) }

    suspend fun list(): List<Profile> = ProfileStore.decode(settings.get(Setting.Profiles))

    suspend fun save(profile: Profile) {
        val current = list()
        val next = if (current.any { it.id == profile.id }) current.map { if (it.id == profile.id) profile else it } else current + profile
        settings.set(Setting.Profiles, ProfileStore.encode(next))
    }

    suspend fun delete(id: String) {
        if (id == ProfileStore.DEFAULT_ID) return
        settings.set(Setting.Profiles, ProfileStore.encode(list().filterNot { it.id == id }))
        if (ProfileStore.activeProfileId == id) ProfileStore.setActive(ProfileStore.DEFAULT_ID)
    }
}

/** Last watched live channel, used by "Açılışta son kanal". */
object LastChannel {
    fun encode(playlistId: String, streamId: String) = "${ProfileStore.activeProfileId}|$playlistId|$streamId"

    /** Returns (playlistId, streamId) when the saved value belongs to the active profile. */
    fun decode(raw: String): Pair<String, String>? {
        val parts = raw.split('|', limit = 3)
        if (parts.size != 3 || parts[0] != ProfileStore.activeProfileId) return null
        return parts[1] to parts[2]
    }
}
