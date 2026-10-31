package com.toonmint.domain;

/**
 * Categoriile de bani urmărite de bookkeeping. Fiecare categorie are o singură
 * direcție posibilă, ca raportul să nu poată deveni inconsistent.
 */
public enum FinanceCategory {

    /** Încasare dintr-o plată (RevenueCat sau înregistrată manual). */
    SALE(FinanceEntryKind.INCOME),
    /** Altă sursă de venit (ex. parteneriat, facturat manual). */
    OTHER_INCOME(FinanceEntryKind.INCOME),
    /** Bani returnați clientului. */
    REFUND(FinanceEntryKind.EXPENSE),
    /** Comisionul magazinului (Apple/Google/Stripe), când este cunoscut. */
    STORE_FEE(FinanceEntryKind.EXPENSE),
    /** Costul providerului AI pentru o generare finalizată. */
    AI_GENERATION(FinanceEntryKind.EXPENSE),
    /** Server, bază de date, Redis, storage. */
    HOSTING(FinanceEntryKind.EXPENSE),
    /** Domeniu, certificate, email tranzacțional. */
    DOMAIN(FinanceEntryKind.EXPENSE),
    /** Publicitate și achiziție de trafic. */
    MARKETING(FinanceEntryKind.EXPENSE),
    /** Orice altă cheltuială (contabilitate, echipamente, taxe). */
    OTHER(FinanceEntryKind.EXPENSE);

    private final FinanceEntryKind kind;

    FinanceCategory(FinanceEntryKind kind) {
        this.kind = kind;
    }

    /**
     * Direcția implicită a categoriei.
     */
    public FinanceEntryKind kind() {
        return kind;
    }
}
