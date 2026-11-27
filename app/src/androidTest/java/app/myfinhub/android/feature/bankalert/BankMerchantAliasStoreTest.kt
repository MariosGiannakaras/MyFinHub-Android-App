package app.myfinhub.android.feature.bankalert

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.myfinhub.android.core.security.AndroidKeystoreCipher
import java.io.File
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BankMerchantAliasStoreTest {
    @Test fun ownerScopedEncryptedMappingSurvivesReopenAndCanBeChangedOrDeleted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File(context.noBackupFilesDir, "alias-test-${UUID.randomUUID()}")
        val cipher = AndroidKeystoreCipher("myfinhub_test_bank_aliases_v1")
        try {
            val store = BankMerchantAliasStore(directory, cipher)
            assertTrue(store.list("test-owner-one").isEmpty())
            store.put("test-owner-one", "SYNTHETIC OWNER SHOP", "Example shop")
            val reopened = BankMerchantAliasStore(directory, cipher)
            assertEquals(listOf(BankMerchantAlias("SYNTHETIC OWNER SHOP", "Example shop")), reopened.list("test-owner-one"))
            assertTrue(reopened.list("test-owner-two").isEmpty())
            assertFalse(directory.listFiles()!!.any { file -> file.readBytes().toString(Charsets.UTF_8).contains("SYNTHETIC OWNER SHOP") || file.name.contains("test-owner") })
            reopened.put("test-owner-one", " synthetic  owner shop ", "New nickname")
            assertEquals("New nickname", store.list("test-owner-one").single().nickname)
            reopened.remove("test-owner-one", "SYNTHETIC OWNER SHOP")
            assertTrue(store.list("test-owner-one").isEmpty())
            assertFalse(validMerchantAliasName("PAN 1234567890123456"))
        } finally { directory.deleteRecursively() }
    }
}
