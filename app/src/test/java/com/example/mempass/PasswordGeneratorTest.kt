package com.example.mempass

import org.junit.Assert.*
import org.junit.Test

class PasswordGeneratorTest {

    @Test
    fun `test random password length and complexity`() {
        val lengths = listOf(8, 16, 32)
        lengths.forEach { len ->
            val pass = PasswordGenerator.generateRandomPassword(len)
            assertEquals(len, pass.length)
            
            // Check if it contains a mix of characters (probabilistically likely for these lengths)
            if (len >= 16) {
                assertTrue(pass.any { it.isLowerCase() })
                assertTrue(pass.any { it.isUpperCase() })
                assertTrue(pass.any { it.isDigit() })
            }
        }
    }

    @Test
    fun `test deterministic password consistency`() {
        val master = "my_master_phrase"
        val service = "google.com"
        val version = 1
        val length = 16

        val pass1 = PasswordGenerator.generateDeterministicPassword(master, service, version, length)
        val pass2 = PasswordGenerator.generateDeterministicPassword(master, service, version, length)
        val passDifferentService = PasswordGenerator.generateDeterministicPassword(master, "facebook.com", version, length)
        val passDifferentVersion = PasswordGenerator.generateDeterministicPassword(master, service, 2, length)

        // Same input must produce same output
        assertEquals(pass1, pass2)
        
        // Different service or version must produce different output
        assertNotEquals(pass1, passDifferentService)
        assertNotEquals(pass1, passDifferentVersion)
        
        assertEquals(length, pass1.length)
    }

    @Test
    fun `test strength calculation levels`() {
        val weak = PasswordGenerator.calculateStrengthDetailed("123")
        val medium = PasswordGenerator.calculateStrengthDetailed("Password123!")
        val strong = PasswordGenerator.calculateStrengthDetailed("Correct-Horse-Battery-Staple-2024!@#")

        assertTrue(weak.score < medium.score)
        assertTrue(medium.score < strong.score)
        
        assertEquals("Very Strong", strong.label)
    }
}
