/*
 * Copyright 2026 OmniOne.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.omnione.did.verifier.v1.protocol.security;

import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Loads IACA trusted root certificates and the verifier's own X.509 key/cert for mdoc flows.
 *
 * <p>Production deployers should replace the example IACA certs in src/main/resources/cert/
 * with actual issuer CA certificates before going live.
 */
@Slf4j
@Component
public class MdocTrustAnchorLoader {

    @Value("${oid4vp.trust.iaca-cert-paths:classpath:cert/*.crt}")
    private String iacaCertPathPattern;

    @Value("${oid4vp.trust.include-jdk-cacerts:true}")
    private boolean includeJdkCacerts;

    @Value("${oid4vp.verifier-x509.cert-path:classpath:cert/verifier_x509.crt}")
    private String verifierCertPath;

    @Value("${oid4vp.verifier-x509.key-path:classpath:cert/verifier_x509.key}")
    private String verifierKeyPath;

    @Getter
    private List<X509Certificate> trustedRoots = Collections.emptyList();

    @Getter
    private X509Certificate verifierCertificate;

    @Getter
    private PrivateKey verifierPrivateKey;

    @PostConstruct
    public void load() {
        List<X509Certificate> roots = new ArrayList<>();

        if (includeJdkCacerts) {
            roots.addAll(loadJdkCacerts());
        }
        roots.addAll(loadIacaCerts());

        trustedRoots = Collections.unmodifiableList(roots);
        log.info("MdocTrustAnchorLoader: loaded {} trusted root certificates", trustedRoots.size());

        loadVerifierAssets();
    }

    private List<X509Certificate> loadIacaCerts() {
        List<X509Certificate> certs = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource[] resources = resolver.getResources(iacaCertPathPattern);
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            for (Resource resource : resources) {
                // Skip verifier_x509.crt — it is not a trust anchor
                if (resource.getFilename() != null && resource.getFilename().startsWith("verifier_x509")) {
                    continue;
                }
                try (InputStream is = resource.getInputStream()) {
                    X509Certificate cert = (X509Certificate) cf.generateCertificate(is);
                    certs.add(cert);
                    log.debug("Loaded IACA cert: {} ({})", resource.getFilename(),
                        cert.getSubjectX500Principal().getName());
                } catch (Exception e) {
                    log.warn("Failed to load IACA cert {}: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Failed to scan IACA cert path '{}': {}", iacaCertPathPattern, e.getMessage());
        }
        log.info("Loaded {} IACA trust anchor certificates", certs.size());
        return certs;
    }

    private List<X509Certificate> loadJdkCacerts() {
        List<X509Certificate> certs = new ArrayList<>();
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null);
            for (TrustManager tm : tmf.getTrustManagers()) {
                if (tm instanceof X509TrustManager) {
                    for (X509Certificate cert : ((X509TrustManager) tm).getAcceptedIssuers()) {
                        certs.add(cert);
                    }
                }
            }
            log.info("Loaded {} JDK default CA certificates", certs.size());
        } catch (Exception e) {
            log.warn("Failed to load JDK default CA certificates: {}", e.getMessage());
        }
        return certs;
    }

    private void loadVerifierAssets() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        try {
            Resource certResource = resolver.getResource(verifierCertPath);
            if (certResource.exists()) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                try (InputStream is = certResource.getInputStream()) {
                    verifierCertificate = (X509Certificate) cf.generateCertificate(is);
                    log.info("Loaded verifier X.509 certificate: CN={}",
                        verifierCertificate.getSubjectX500Principal().getName());
                }
            } else {
                log.warn("Verifier X.509 certificate not found at: {}", verifierCertPath);
            }
        } catch (Exception e) {
            log.warn("Failed to load verifier certificate: {}", e.getMessage());
        }

        try {
            Resource keyResource = resolver.getResource(verifierKeyPath);
            if (keyResource.exists()) {
                verifierPrivateKey = parsePkcs8PemKey(keyResource);
                log.info("Loaded verifier private key (algorithm: {})",
                    verifierPrivateKey != null ? verifierPrivateKey.getAlgorithm() : "null");
            } else {
                log.warn("Verifier private key not found at: {}", verifierKeyPath);
            }
        } catch (Exception e) {
            log.warn("Failed to load verifier private key: {}", e.getMessage());
        }
    }

    private PrivateKey parsePkcs8PemKey(Resource keyResource) throws Exception {
        String pem;
        try (InputStream is = keyResource.getInputStream()) {
            pem = new String(is.readAllBytes());
        }
        // Strip PEM headers
        String base64 = pem
            .replaceAll("-----BEGIN (?:EC |RSA )?PRIVATE KEY-----", "")
            .replaceAll("-----END (?:EC |RSA )?PRIVATE KEY-----", "")
            .replaceAll("\\s+", "");
        byte[] der = Base64.getDecoder().decode(base64);

        // Try EC first, then RSA
        try {
            return KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (Exception ignored) {
            return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
        }
    }
}
