package app.myfinhub.android.designsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebCategoryIconPolicyTest {
    @Test fun recognizesPackCatalogAndLegacySemanticSelections() {
        assertEquals("tabler:coffee", WebCategoryIconPolicy.explicit("tabler", "coffee", mapOf("tabler" to "icon/coffee")))
        assertEquals("lucide:coffee", WebCategoryIconPolicy.explicit(null, "coffee", null))
        assertEquals("heroicons:academic-cap", WebCategoryIconPolicy.explicit("heroicons", "heroicons:icon/academic-cap", null))
        assertNull(WebCategoryIconPolicy.explicit("bootstrap", "lucide:icon/coffee", null))
        assertNull(WebCategoryIconPolicy.explicit("tabler", "tabler:../../secret", null))
    }

    @Test fun distinctSubcategoryAndParentUseExactKeysBeforeSemanticFallback() {
        val selected = mapOf("expense:Φαγητό" to "lucide:utensils", "expense:Φαγητό:Καφές" to "lucide:coffee")
        assertEquals(listOf("lucide:coffee", "lucide:utensils"),
            WebCategoryIconPolicy.candidates("expense", "Φαγητό", "Καφές", "lucide", selected))
        assertEquals("lucide:utensils",
            WebCategoryIconPolicy.candidates("expense", "Φαγητό", "Άγνωστο", "lucide", selected).first())
        assertEquals("lucide:coffee",
            WebCategoryIconPolicy.candidates("expense", "Φαγητό", "Καφές", "lucide", emptyMap()).first())
        assertEquals(emptyList<String>(),
            WebCategoryIconPolicy.candidates("transfer", null, null, "lucide", selected))
    }
}
