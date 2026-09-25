package app.myfinhub.android.feature.utilities

import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDiagnosticsPresentationTest {
    @Test
    fun activeVerifiedSession_doesNotSurfaceAssuranceAcronym() {
        val value = humanReadableSessionStatus("Ενεργή · AAL2")

        assertEquals("Συνδεδεμένη και επαληθευμένη", value)
        assertFalse(value.contains("AAL", ignoreCase = true))
    }

    @Test
    fun requiredVerification_isHumanReadable() {
        val value = humanReadableSessionStatus("Απαιτεί AAL2")

        assertEquals("Χρειάζεται επιπλέον επαλήθευση", value)
    }

    @Test
    fun offlineCache_isPresentedAsLocalCopy() {
        val value = humanReadableSyncStatus("Offline cache · 2 εκκρεμείς")

        assertEquals("Τοπικό αντίγραφο · 2 εκκρεμείς", value)
        assertFalse(value.contains("offline", ignoreCase = true))
    }

    @Test
    fun syncTimestamp_isConvertedToRequestedLocalZone() {
        val value = formatDiagnosticTime(
            rawTimestamp = "2026-09-10T12:30:00Z",
            zoneId = ZoneId.of("Europe/Athens"),
        )

        assertTrue(value.contains("2026"))
        assertTrue(value.endsWith("15:30"))
        assertFalse(value.contains("T12:30"))
    }

    @Test
    fun privacySafeCode_getsFriendlyDescriptionWithoutLosingSupportCodeContract() {
        val value = diagnosticCodeDescription("MFH-API-SERVER-503")

        assertEquals("Η υπηρεσία MyFinHub δεν ήταν προσωρινά διαθέσιμη.", value)
    }
}
