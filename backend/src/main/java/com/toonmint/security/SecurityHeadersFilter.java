package com.toonmint.security;

import com.toonmint.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Adaugă headerele de securitate pe fiecare răspuns.
 *
 * <p>Content-Security-Policy trebuie să permită exact gazdele etichetelor pe care le încărcăm
 * din frontend (GTM, GA4, AdSense, Clarity). Dacă o gazdă lipsește, browserul descarcă scriptul
 * dar blochează execuția — simptomul clasic este „tag not installed” în Google, cu eroare de CSP
 * în consolă. `https://*.google.com` NU acoperă `googletagmanager.com`, `google-analytics.com`
 * sau `analytics.google.com`; ele trebuie listate separat.
 *
 * <p>Politica este construită o singură dată, la pornire, din lista de gazde de mai jos plus
 * originile permise prin CORS (frontendul).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityHeadersFilter extends OncePerRequestFilter {

    /** AdSense și tot ce apelează scripturile injectate de el. */
    private static final String GOOGLE_ADS_HOSTS = String.join(" ",
            "https://*.googlesyndication.com",
            "https://*.googleadservices.com",
            "https://*.doubleclick.net",
            "https://*.google.com",
            "https://*.gstatic.com",
            "https://*.adtrafficquality.google");

    /** GTM, gtag.js și colectarea măsurătorilor. */
    private static final String GOOGLE_TAG_HOSTS = String.join(" ",
            "https://www.googletagmanager.com",
            "https://*.googletagmanager.com",
            "https://www.google-analytics.com",
            "https://*.google-analytics.com",
            "https://*.analytics.google.com");

    /** Microsoft Clarity: recorderul și endpoint-urile de date. */
    private static final String MICROSOFT_CLARITY_HOSTS = String.join(" ",
            "https://www.clarity.ms",
            "https://*.clarity.ms");

    private static final String THIRD_PARTY_HOSTS =
            GOOGLE_ADS_HOSTS + " " + GOOGLE_TAG_HOSTS + " " + MICROSOFT_CLARITY_HOSTS;

    private static final String PERMISSIONS_POLICY = String.join(", ",
            "camera=()",
            "microphone=()",
            "geolocation=()",
            "payment=(self \"https://pay.rev.cat\")",
            "usb=()");

    private final String contentSecurityPolicy;

    public SecurityHeadersFilter(AppProperties properties) {
        this.contentSecurityPolicy = buildPolicy(properties);
    }

    /**
     * `script-src` acceptă 'unsafe-inline' și 'unsafe-eval' pentru că AdSense injectează scripturi
     * inline și folosește eval. Restul directivelor rămân cât se poate de înguste.
     *
     * <p>Originile permise prin CORS (frontendul) intră în img-src și connect-src, ca aplicația
     * să funcționeze și când UI-ul și API-ul sunt pe domenii diferite.
     */
    private static String buildPolicy(AppProperties properties) {
        List<String> origins = properties.cors() == null || properties.cors().allowedOrigins() == null
                ? List.of()
                : properties.cors().allowedOrigins();
        String appOrigins = origins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .collect(Collectors.joining(" "));

        String policy = String.join("; ",
                "default-src 'self'",
                "script-src 'self' 'unsafe-inline' 'unsafe-eval' " + THIRD_PARTY_HOSTS,
                "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com",
                "font-src 'self' https://fonts.gstatic.com",
                "img-src 'self' data: blob: " + THIRD_PARTY_HOSTS + " " + appOrigins,
                "connect-src 'self' " + THIRD_PARTY_HOSTS + " " + appOrigins,
                "frame-src 'self' " + GOOGLE_TAG_HOSTS,
                "form-action 'self'",
                "frame-ancestors 'none'",
                "base-uri 'self'",
                "object-src 'none'");
        return policy.replaceAll("\\s+", " ").trim();
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        response.setHeader("Content-Security-Policy", contentSecurityPolicy);
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Permissions-Policy", PERMISSIONS_POLICY);
        // HSTS are sens doar pe HTTPS; pe HTTP browserul îl ignoră, dar îl trimitem corect.
        if (request.isSecure()) {
            response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
        }
        filterChain.doFilter(request, response);
    }

    /**
     * Nu aplicăm politica pe documentația OpenAPI generată, care își încarcă resursele din webjars
     * și ar putea primi o politică incompatibilă.
     */
    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/v3/api-docs")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/actuator");
    }
}
