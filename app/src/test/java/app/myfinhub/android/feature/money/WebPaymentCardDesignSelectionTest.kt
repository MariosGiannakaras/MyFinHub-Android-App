package app.myfinhub.android.feature.money

import org.junit.Assert.assertEquals
import org.junit.Test

class WebPaymentCardDesignSelectionTest {
    private fun card(
        id: String,
        bankId: String,
        kind: String = "debit",
        form: String = "physical",
        design: String? = null,
        network: String = "visa",
    ) = MoneyCard(
        id = id,
        nickname = id,
        last4 = "1234",
        kind = if (kind == "credit") "Πιστωτική" else "Χρεωστική",
        currentBalance = 0.0,
        limit = null,
        vaultState = VaultState.LOCKED,
        canonicalKind = kind,
        bankId = bankId,
        network = network,
        formFactor = form,
        designId = design,
    )

    @Test fun persistedDesignIdWinsOverBankAndFormFactor() {
        assertEquals("revolut-ruby",
            referenceVisualTemplateId(card("one", "piraeus", design = "revolut-ruby")))
        assertEquals("alpha-bonus",
            referenceVisualTemplateId(card("two", "alpha", kind = "debit", design = "alpha-bonus")))
    }

    @Test fun inferredDefaultsMatchWebCardDesignCatalog() {
        assertEquals("piraeus-yellow", referenceVisualTemplateId(card("a","piraeus")))
        assertEquals("piraeus-virtual", referenceVisualTemplateId(card("b","piraeus",form="virtual")))
        assertEquals("piraeus-green", referenceVisualTemplateId(card("c","piraeus",kind="credit")))
        assertEquals("alpha-bonus", referenceVisualTemplateId(card("d","alpha",kind="credit")))
        assertEquals("payzy", referenceVisualTemplateId(card("e","payzy",form="virtual")))
        assertEquals("payzy-physical", referenceVisualTemplateId(card("f","payzy",form="physical")))
        assertEquals("viva-digital", referenceVisualTemplateId(card("g","viva",form="virtual")))
    }

    @Test fun differentCardIdsNeverRandomlyChooseAnotherBankDesign() {
        assertEquals(referenceVisualTemplateId(card("z", "revolut")),
            referenceVisualTemplateId(card("uuid-thing", "revolut")))
        assertEquals("custom", referenceVisualTemplateId(card("z", "unknown")))
        assertEquals("custom", referenceVisualTemplateId(card("a", "unknown", design="no-such-design")))
    }
}
