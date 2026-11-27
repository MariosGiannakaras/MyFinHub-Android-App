package app.myfinhub.android.feature.bankalert

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankSmsInboxStoreTest {
    private val message = """
        ALERT-ΠΙΣΤΩΤΙΚΗ ΚΑΡΤΑ
        ΑΓΟΡΑ 18/10/2026 21:17
        ΚΑΡΤΑ: 1234***9876
        ΠΟΣΟ= 17.43 EUR
        ΑΠΟ: SYNTHETIC SMS STORE
        ΧΩΡΑ: ΕΛΛΑΔΑ
    """.trimIndent()

    @Test fun receiverAdmissionDedupOwnerIsolationAndConfirmation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = "synthetic-${UUID.randomUUID()}"
        val other = "other-${UUID.randomUUID()}"
        val directory = File(context.noBackupFilesDir, "inbox-test-${UUID.randomUUID()}")
        val store = BankSmsInboxStore(context, directory)
        try {
            store.disable()
            assertFalse(store.receive("Piraeus", 100, message))
            assertTrue(store.enable(owner))
            assertFalse(store.receive("Unknown", 100, message))
            assertFalse(store.receive("Piraeus", 100, "PIRAEUS OTP 123456"))
            assertTrue(store.receive("Piraeus", 100, message))
            assertFalse(store.receive("Piraeus", 100, message))
            assertTrue(store.receive("Piraeus", 101, message))
            val reopened = BankSmsInboxStore(context, directory)
            val rows = reopened.list(owner)
            assertEquals(2, rows.size)
            assertEquals(2, rows.map { it.eventId }.distinct().size)
            assertEquals(1743L, rows.first().cents)
            assertEquals("9876", rows.first().lastFour)
            assertEquals("", rows.first().alert().accountHint)
            assertTrue(reopened.list(other).isEmpty())
            assertTrue(directory.listFiles().orEmpty().none { it.readBytes().toString(Charsets.UTF_8).contains("SYNTHETIC SMS STORE") })
            reopened.reconcileConfirmed(owner, setOf(rows.first().eventId))
            assertEquals(listOf(rows.last()), reopened.list(owner))
            assertFalse(reopened.receive("Piraeus", 101, message))
            reopened.reconcileOwner(other)
            assertFalse(reopened.enabled(owner))
            assertFalse(reopened.receive("Piraeus", 102, message))
            reopened.discard(owner, rows.last().id)
            assertTrue(reopened.list(owner).isEmpty())
        } finally { store.disable(); directory.deleteRecursively() }
    }
}
