package zhiqiu.app.cs.core

import com.russhwolf.settings.MapSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AppSettingsRoomSessionTest {

    private val key = ByteArray(RoomCrypto.KEY_SIZE) { (it + 1).toByte() }

    private fun newSettings() = AppSettings(MapSettings(mutableMapOf()))

    @Test
    fun saves_and_reads_back() {
        val settings = newSettings()
        settings.saveRoomSession("ROOM-A", key)
        val restored = settings.roomSession()
        assertNotNull(restored)
        assertEquals("ROOM-A", restored.first)
        assertEquals(key.toList(), restored.second.toList())
        assertEquals(true, settings.hasRoomSession())
    }

    @Test
    fun refuses_to_save_a_key_of_the_wrong_size() {
        val settings = newSettings()
        settings.saveRoomSession("ROOM-A", ByteArray(RoomCrypto.KEY_SIZE - 1))
        assertNull(settings.roomSession())
    }

    @Test
    fun disabling_remember_clears_the_saved_session() {
        val settings = newSettings()
        settings.saveRoomSession("ROOM-A", key)
        settings.setRememberRoom(false)
        assertNull(settings.roomSession())
        assertEquals(false, settings.hasRoomSession())
    }

    @Test
    fun clearing_drops_the_session() {
        val settings = newSettings()
        settings.saveRoomSession("ROOM-A", key)
        settings.clearRoomSession()
        assertNull(settings.roomSession())
    }
}
