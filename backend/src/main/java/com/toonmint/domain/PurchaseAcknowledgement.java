package com.toonmint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Dovada că utilizatorul a solicitat expres livrarea imediată a conținutului digital
 * și a acceptat că își pierde dreptul de retragere de 14 zile.
 *
 * <p>Temei: OUG 34/2014 art. 16 lit. m) — fără această confirmare, consumatorul poate
 * cere rambursarea și după ce a consumat creditele. Înregistrarea se face înainte de
 * deschiderea checkout-ului.
 */
@Entity
@Table(name = "purchase_acknowledgements")
public class PurchaseAcknowledgement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(length = 20)
    private String kind;

    @Column(name = "terms_version", length = 20)
    private String termsVersion;

    @Column(name = "immediate_delivery", nullable = false)
    private boolean immediateDelivery = true;

    @Column(name = "withdrawal_waived", nullable = false)
    private boolean withdrawalWaived = true;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "accepted_at", nullable = false)
    private Instant acceptedAt;

    /**
     * Constructor cerut de JPA.
     */
    protected PurchaseAcknowledgement() {
    }

    /**
     * Creează o confirmare pentru un produs achiziționat de utilizatorul dat.
     */
    public PurchaseAcknowledgement(User user, String productId, String kind, String termsVersion, String clientIp) {
        this.user = user;
        this.productId = productId;
        this.kind = kind;
        this.termsVersion = termsVersion;
        this.clientIp = clientIp;
    }

    /**
     * Completează momentul acceptării înainte de inserare.
     */
    @PrePersist
    void onCreate() {
        if (acceptedAt == null) {
            acceptedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getProductId() {
        return productId;
    }

    public String getKind() {
        return kind;
    }

    public String getTermsVersion() {
        return termsVersion;
    }

    public boolean isImmediateDelivery() {
        return immediateDelivery;
    }

    public boolean isWithdrawalWaived() {
        return withdrawalWaived;
    }

    public String getClientIp() {
        return clientIp;
    }

    public Instant getAcceptedAt() {
        return acceptedAt;
    }
}
