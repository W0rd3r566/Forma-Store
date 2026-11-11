package dev.formastore.app

import org.junit.Assert.*
import org.junit.Test

class VersionTest {
    @Test fun stableVersionMapping() {
        assertEquals(100, versionCode("0.1.0"))
        assertEquals(20103, versionCode("2.1.3"))
        assertTrue(versionCode("0.2.0")!! > versionCode("0.1.99")!!)
    }
    @Test fun rejectsMalformedVersions() {
        for (value in listOf("1.2", "1.2.3-beta", "v1.2.3", "1.100.0", "01.2.3", "-1.2.3", "999999999999.1.1")) assertNull(versionCode(value))
    }
}
