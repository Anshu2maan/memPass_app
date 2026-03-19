package com.example.mempass.common

object Constants {
    // Security Config
    const val PIN_LENGTH = 6
    const val MAX_FAILED_ATTEMPTS = 5
    const val BASE_LOCKOUT_MS = 30 * 1000L 
    const val MAX_LOCKOUT_MS = 24 * 60 * 60 * 1000L 
    const val RECOVERY_KEY_LENGTH = 24
    const val GRACE_PERIOD_MS = 5 * 60 * 1000L 
    
    // Argon2id Parameters
    const val ARGON2_ITERATIONS = 3
    const val ARGON2_MEMORY = 65536 
    const val ARGON2_PARALLELISM = 1
    const val AES_KEY_SIZE = 256
    
    // UI & Health Thresholds
    const val WEAK_PASSWORD_THRESHOLD = 0.5f
    const val BACKUP_REMINDER_DAYS = 7
    const val EXPIRY_WARNING_DAYS = 15
    
    // Storage File Names
    const val PREFS_SECURITY = "mempass_security_v1"
    const val PREFS_HISTORY = "mempass_history_v1"
    
    // Pref Keys
    const val KEY_PIN_HASH = "pin_hash"
    const val KEY_ARGON2_SALT = "argon2_salt"
    const val KEY_DERIVATION_TYPE = "derivation_type"
    const val KEY_MASTER_KEY_BY_PIN = "mk_pin"
    const val KEY_MASTER_KEY_BY_RECOVERY = "mk_recovery"
    const val KEY_RECOVERY_KEY_HASH = "encrypted_recovery_key"
    const val KEY_RECOVERY_KEY_STORED = "rk_backup_wrapped" 
    const val KEY_FAILED_ATTEMPTS = "fail_count"
    const val KEY_LOCKOUT_UNTIL = "lock_ts"
    const val KEY_AUTO_BACKUP_ENABLED = "auto_backup"
    const val KEY_SCREEN_SECURITY_ENABLED = "screen_security"
    const val KEY_THEME_PREF = "ui_theme"
    const val KEY_LAST_BACKUP_TIME = "last_backup_time"
    const val KEY_IS_VAULT_DIRTY = "is_vault_dirty"
    
    // Worker Intervals
    const val DEFAULT_BACKUP_INTERVAL_HOURS = 24L
}
