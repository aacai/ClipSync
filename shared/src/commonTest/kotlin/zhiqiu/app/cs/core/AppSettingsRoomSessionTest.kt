package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import zhiqiu.crypto.toHex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppSettingsRoomSessionTest {

    private val key = ByteArray(RoomCrypto.KEY_SIZE) { (it + 1).toByte() }
    private val raw = mutableMapOf<String, Any>()

    private fun newSettings(seed: String = SEED) = AppSettings(MapSettings(raw), seed)

    @Test
    fun saves_and_reads_back() {
        val settings = newSettings()
        settings.saveRoomSession("ROOM-A", key, "hunter2")
        val restored = settings.roomSession()
        assertNotNull(restored)
        assertEquals("ROOM-A", restored.first)
        assertEquals(key.toList(), restored.second.toList())
        assertEquals(true, settings.hasRoomSession())
        assertEquals("hunter2", settings.lastRoomPassword())
    }

    @Test
    fun stored_credentials_carry_no_plaintext() {
        newSettings().saveRoomSession("ROOM-A", key, "hunter2")
        val storedKey = raw["room.key"] as String
        val storedPassword = raw["room.password"] as String
        assertFalse(storedPassword.contains("hunter2"))
        assertNotEquals(key.toHex(), storedKey)
        assertEquals("ROOM-A", raw["room.code"])
    }

    @Test
    fun another_install_cannot_unlock_the_saved_session() {
        newSettings().saveRoomSession("ROOM-A", key, "hunter2")
        val other = newSettings(seed = "another-install")
        assertNull(other.roomSession())
        assertEquals("", other.lastRoomPassword())
        assertEquals("ROOM-A", other.lastRoomCode())
    }

    @Test
    fun leftover_plain_text_in_settings_unlocks_nothing() {
        raw["room.code"] = "ROOM-A"
        raw["room.key"] = "QWJjZGVm"
        raw["room.password"] = "hunter2"
        val settings = newSettings()
        assertNull(settings.roomSession())
        assertEquals("", settings.lastRoomPassword())
        assertEquals("ROOM-A", settings.lastRoomCode())
    }

    @Test
    fun refuses_to_save_a_key_of_the_wrong_size() {
        val settings = newSettings()
        settings.saveRoomSession("ROOM-A", ByteArray(RoomCrypto.KEY_SIZE - 1), "hunter2")
        assertNull(settings.roomSession())
        assertNull(settings.lastRoomCode())
        assertEquals("", settings.lastRoomPassword())
    }

    @Test
    fun disabling_remember_drops_the_credentials_but_keeps_the_code() {
        val settings = newSettings()
        settings.saveRoomSession("ROOM-A", key, "hunter2")
        settings.setRememberRoom(false)
        assertNull(settings.roomSession())
        assertEquals(false, settings.hasRoomSession())
        assertEquals("ROOM-A", settings.lastRoomCode())
        assertEquals("", settings.lastRoomPassword())
    }

    @Test
    fun forgetting_the_key_leaves_the_code_for_the_next_try() {
        val settings = newSettings()
        settings.saveRoomSession("ROOM-A", key, "hunter2")
        settings.forgetRoomKey()
        assertNull(settings.roomSession())
        assertEquals("ROOM-A", settings.lastRoomCode())
        assertEquals("", settings.lastRoomPassword())
        assertFalse(raw.containsKey("room.password"))
    }

    @Test
    fun a_room_code_from_an_unremembered_room_is_still_recalled() {
        val settings = newSettings()
        settings.setRememberRoom(false)
        settings.saveRoomSession("ROOM-A", key, "hunter2")
        assertNull(settings.roomSession())
        assertEquals("ROOM-A", settings.lastRoomCode())
        assertEquals("", settings.lastRoomPassword())
    }

    @Test
    fun a_room_without_a_password_stays_without_one() {
        val settings = newSettings()
        settings.saveRoomSession("ROOM-A", key, "")
        assertNotNull(settings.roomSession())
        assertEquals("", settings.lastRoomPassword())
    }

    private companion object {
        const val SEED = "test-install"
    }
}
