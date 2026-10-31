package com.toonmint.domain;

/**
 * Direcția unei intrări financiare. Suma stocată este mereu pozitivă; semnul
 * este dat de tip (o rambursare este o cheltuială, nu o încasare negativă).
 */
public enum FinanceEntryKind {
    INCOME,
    EXPENSE
}
