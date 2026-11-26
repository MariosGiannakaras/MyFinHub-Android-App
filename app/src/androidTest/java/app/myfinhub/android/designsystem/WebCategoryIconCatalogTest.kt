package app.myfinhub.android.designsystem

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WebCategoryIconCatalogTest {
    @Test fun pinnedArtworkFromEveryPackIsAvailableOffline() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val ids = listOf("lucide:coffee", "lucide:utensils", "tabler:coffee", "phosphor:acorn",
            "heroicons:academic-cap", "bootstrap:0-circle")
        val images = ids.map { WebCategoryIconCatalog.get(assets, it).also { result -> assertNotNull(it, result) } }
        assertNotEquals(images[0], images[1])
        assertNull(WebCategoryIconCatalog.get(assets, "tabler:../../secret"))
        assertNull(WebCategoryIconCatalog.get(assets, "lucide:no-such-catalog-icon"))
    }
}
