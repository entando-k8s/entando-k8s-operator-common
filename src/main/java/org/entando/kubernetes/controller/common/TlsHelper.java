/*
 *
 * Copyright 2015-Present Entando Inc. (http://www.entando.com) All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 *  This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 */

package org.entando.kubernetes.controller.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import org.apache.commons.lang3.RandomStringUtils;
import org.entando.kubernetes.controller.EntandoOperatorConfig;

public final class TlsHelper {

    public static final String TLS_KEY = "tls.key";
    public static final String TLS_CRT = "tls.crt";
    private static final TlsHelper INSTANCE = new TlsHelper();
    private final char[] trustStorePassword = RandomStringUtils.randomAlphanumeric(20).toCharArray();
    private Optional<KeyStore> trustStore = Optional.empty();
    private Optional<String> trustStoreBase64 = Optional.empty();

    private TlsHelper() {
    }

    public static TlsHelper getInstance() {
        return INSTANCE;
    }

    public static boolean isDefaultTlsKeyPairAvailable() {
        return EntandoOperatorConfig.getPathToDefaultTlsKeyPair()
                .map(path -> path.resolve(TLS_CRT).toFile().exists() && path.resolve(TLS_KEY).toFile().exists()).orElse(false);
    }

    public static boolean canAutoCreateTlsSecret() {
        return EntandoOperatorConfig.useAutoCertGeneration() || isDefaultTlsKeyPairAvailable();
    }

    public static String getDefaultProtocol() {
        return canAutoCreateTlsSecret() ? "https" : "http";
    }

    public void init() {
        List<Path> caPaths = EntandoOperatorConfig.getCertificateAuthorityCertPaths();
        if (caPaths.isEmpty()) {
            this.trustStore = Optional.empty();
        } else {
            populateTrustStore(caPaths);
        }
    }

    public String getTrustStoreBase64() {
        return trustStoreBase64.orElseThrow(IllegalStateException::new);
    }

    public String getTlsCaCertBase64(Path path) {
        return base64EncodeContent(path);
    }

    public String getTlsCertBase64() {
        return loadSslFile(TLS_CRT);
    }

    public String getTlsKeyBase64() {
        return loadSslFile(TLS_KEY);
    }

    public boolean isTrustStoreAvailable() {
        return trustStore.isPresent();
    }

    public String getTrustStorePassword() {
        return new String(trustStorePassword);
    }

    private void populateTrustStore(List<Path> caPaths) {
        try {
            KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            keyStore.load(null, null);
            caPaths.forEach(certPath -> importCert(keyStore, certPath));
            Path tempFile = Files.createTempFile("trust-store", ".jks");
            TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance("PKIX");
            trustManagerFactory.init((KeyStore) null);
            TrustManager[] trustManagers = trustManagerFactory.getTrustManagers();
            X509TrustManager trustManager = (X509TrustManager) trustManagers[0];
            for (X509Certificate certificate : trustManager.getAcceptedIssuers()) {
                keyStore.setCertificateEntry(certificate.getSubjectX500Principal().getName(), certificate);
            }
            try (OutputStream stream = Files.newOutputStream(tempFile)) {
                keyStore.store(stream, trustStorePassword);
            }
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                keyStore.store(outputStream, trustStorePassword);
                trustStoreBase64 = Optional.of(new String(Base64.getEncoder().encode(outputStream.toByteArray()), StandardCharsets.UTF_8));
            }
            System.setProperty("javax.net.ssl.trustStore", tempFile.normalize().toAbsolutePath().toString());
            System.setProperty("javax.net.ssl.trustStorePassword", new String(trustStorePassword));
            this.trustStore = Optional.of(keyStore);
        } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
            throw new IllegalStateException(e);
        }
    }

    private void importCert(KeyStore keyStore, Path certPath) {
        try (InputStream stream = Files.newInputStream(certPath)) {
            Certificate cert = CertificateFactory.getInstance("x.509").generateCertificate(stream);
            keyStore.setCertificateEntry(certPath.getFileName().toString(), cert);
        } catch (IOException | KeyStoreException | CertificateException e) {
            throw new IllegalStateException(e);
        }
    }

    private String loadSslFile(String other) {
        return EntandoOperatorConfig.getPathToDefaultTlsKeyPair().map(path -> base64EncodeContent(path.resolve(other)))
                .orElseThrow(IllegalStateException::new);
    }

    private String base64EncodeContent(Path path) {
        try {
            return new String(Base64.getEncoder().encode(Files.readAllBytes(path)),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

}
