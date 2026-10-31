package com.toonmint.domain;

/**
 * Originea unei intrări financiare: scrisă automat de pipeline sau de webhook,
 * ori introdusă manual de operator.
 */
public enum FinanceSource {
    AUTO_JOB,
    AUTO_REVENUECAT,
    MANUAL
}
