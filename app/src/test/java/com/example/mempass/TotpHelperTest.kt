package com.example.mempass

import org.junit.Assert.*
import org.junit.Test

class TotpHelperTest {

    @Test
    fun testGenerateTotpWithKnownSecret() {
        val secret = "JBSWY3DPEHPK3PXP".toCharArray()
        val code = TotpHelper.generateTotp(secret)
        
        assertEquals(6, code.length)
        assertTrue(code.all { it.isDigit() })
    }

    @Test
    fun testGenerateTotpWithEmptySecret() {
        val code = TotpHelper.generateTotp(CharArray(0))
        assertEquals("------", code)
    }

    @Test
    fun testGenerateTotpWithInvalidSecret() {
        // Contains invalid Base32 character '1'
        val secret = "JBSWY3DPEHPK3PX1".toCharArray()
        val code = TotpHelper.generateTotp(secret)
        assertEquals("------", code)
    }

    @Test
    fun testIsValidSecret() {
        assertTrue(TotpHelper.isValidSecret("JBSWY3DPEHPK3PXP".toCharArray()))
        assertTrue(TotpHelper.isValidSecret("jbswy3dpehpk3pxp".toCharArray()))
        assertTrue(TotpHelper.isValidSecret("JBSW Y3DP EHPK 3PXP".toCharArray()))
        assertFalse(TotpHelper.isValidSecret("JBSW13DP".toCharArray())) // '1' is invalid
        assertFalse(TotpHelper.isValidSecret("".toCharArray()))
    }
}
