package app.myfinhub.android.core.auth

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Test

class JwtClaimsParserTest {
    private val parser = JwtClaimsParser()
    private fun token(payload: String): String =
        "header." + Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray()) + ".signature"

    @Test fun readsOnlySupportedAssuranceHintsFromUnpaddedBase64Url() {
        assertEquals(AssuranceLevel.AAL1, parser.assuranceLevel(token("""{"aal":"aal1"}""")))
        assertEquals(AssuranceLevel.AAL2, parser.assuranceLevel(token("""{"aal":"aal2"}""")))
        assertEquals(AssuranceLevel.UNKNOWN, parser.assuranceLevel(token("""{"aal":"aal3"}""")))
        assertEquals(AssuranceLevel.UNKNOWN, parser.assuranceLevel(token("{}")))
        assertEquals(AssuranceLevel.UNKNOWN, parser.assuranceLevel("header.!!!.signature"))
        assertEquals(AssuranceLevel.UNKNOWN, parser.assuranceLevel("invalid"))
    }
}
