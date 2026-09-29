package app.myfinhub.android.designsystem

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrandAssetsSourceTest {
    private fun repoFile(path: String): File {
        val candidates = listOf(File(path), File("..", path))
        return candidates.firstOrNull { it.isFile }
            ?: error("Missing repository asset: $path")
    }

    @Test
    fun adaptiveLauncherUsesPlatformLayers() {
        val v26 = repoFile("app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml").readText()
        val v33 = repoFile("app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml").readText()
        assertTrue(v26.contains("@color/ic_launcher_background"))
        assertTrue(v26.contains("@drawable/myfinhub_launcher_foreground"))
        assertFalse(v26.contains("<monochrome"))
        assertTrue(v33.contains("@drawable/myfinhub_launcher_foreground"))
        assertTrue(v33.contains("@drawable/ic_launcher_monochrome"))

        val monochrome = repoFile("app/src/main/res/drawable/ic_launcher_monochrome.xml").readText()
        assertTrue(monochrome.contains("<vector"))
        assertTrue(monochrome.contains("<path"))
        assertFalse(monochrome.contains("<bitmap"))
    }

    @Test
    fun brandingBitmapsHaveIntentionalDimensions() {
        val expected = mapOf(
            "app/src/main/res/drawable-nodpi/myfinhub_launcher_foreground.png" to (1080 to 1080),
            "app/src/main/res/drawable-nodpi/myfinhub_symbol.png" to (512 to 512),
            "branding/google-play-icon.png" to (512 to 512),
        )
        expected.forEach { (path, size) ->
            val image = ImageIO.read(repoFile(path))
            assertEquals("$path width", size.first, image.width)
            assertEquals("$path height", size.second, image.height)
        }

        val lockup = ImageIO.read(repoFile("app/src/main/res/drawable-nodpi/myfinhub_lockup.png"))
        assertEquals(800, lockup.width)
        assertTrue(lockup.height in 260..300)
    }

    @Test
    fun duplicateLegacyBrandResourcesStayRemoved() {
        for (path in listOf(
            "app/src/main/res/drawable-nodpi/myfinhub_brand_light.png",
            "app/src/main/res/drawable-nodpi/myfinhub_brand_dark.png",
            "app/src/main/res/drawable-nodpi/myfinhub_lockup_light.png",
            "app/src/main/res/drawable-nodpi/myfinhub_lockup_dark.png",
            "app/src/main/res/drawable-nodpi/myfinhub_symbol_monochrome.png",
            "app/src/main/res/drawable/ic_launcher_foreground.xml",
        )) {
            assertFalse(File(path).exists() || File("..", path).exists())
        }
    }
}
