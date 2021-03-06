/*
 * Copyright (c) 2016 CA. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 *
 */

package com.ca.mas.core.io.http;

import com.ca.mas.core.cert.PublicKeyHash;
import com.ca.mas.core.cert.TrustedCertificateConfiguration;

import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

/**
 * Trust manager that works with a {@link com.ca.mas.core.cert.TrustedCertificateConfiguration}.
 */
public class TrustedCertificateConfigurationTrustManager implements X509TrustManager {
    private final Collection<X509TrustManager> publicPkiDelegates;
    private final Collection<X509TrustManager> privateTrustStoreDelegates;
    private final Set<PublicKeyHash> pinnedPublicKeys;

    /**
     * Create a trust manager that uses the specified trust configuration.
     *
     * @param trustConf trusted cert configuration to use for setting up the trust manager.  Required.
     */
    public TrustedCertificateConfigurationTrustManager(TrustedCertificateConfiguration trustConf) {
        this.publicPkiDelegates = trustConf.isAlsoTrustPublicPki() ? getPlatformX509TrustManagers() : null;
        this.privateTrustStoreDelegates = getPrivateX509TrustManagers(trustConf.getTrustedCertificateAnchors());
        Collection<PublicKeyHash> pins = trustConf.getTrustedCertificatePinnedPublicKeyHashes();
        this.pinnedPublicKeys = pins == null ? null : new HashSet<PublicKeyHash>(pins);
    }

    private static Collection<X509TrustManager> getPrivateX509TrustManagers(Collection<X509Certificate> certs) {
        return getX509TrustManagers(createTrustStoreWithCerts(certs));
    }

    private static Collection<X509TrustManager> getPlatformX509TrustManagers() {
        Collection<X509TrustManager> xtms = getX509TrustManagers(null);
        if (xtms.isEmpty())
            throw new RuntimeException("Cannot trust public PKI -- no default X509TrustManager found");
        return xtms;
    }

    private static KeyStore createTrustStoreWithCerts(Collection<X509Certificate> certs) {
        try {
            int a = 1;
            KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(null, null);
            for (X509Certificate cert : certs) {
                String alias = "cert" + a++;
                ks.setCertificateEntry(alias, cert);
            }
            return ks;
        } catch (KeyStoreException | CertificateException | NoSuchAlgorithmException | IOException e) {
            throw new RuntimeException("Unable to create trust store of default KeyStore type: " + e.getMessage(), e);
        }
    }

    private static Collection<X509TrustManager> getX509TrustManagers(KeyStore trustStore) {
        Collection<X509TrustManager> xtms = new ArrayList<X509TrustManager>();

        TrustManagerFactory tmf;
        try {
            tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("No default TrustManagerFactory implementation available: " + e.getMessage(), e);
        } catch (KeyStoreException e) {
            throw new RuntimeException("Unable to obtain platform X.509 trust managers: " + e.getMessage(), e);
        }

        TrustManager[] tms = tmf.getTrustManagers();
        for (TrustManager tm : tms) {
            if (tm instanceof X509TrustManager) {
                X509TrustManager xtm = (X509TrustManager) tm;
                xtms.add(xtm);
            }
        }
        return xtms;
    }

    private CertificateException checkPrivateTrustStoreDelegates(X509Certificate[] chain, String s) {
        for (X509TrustManager delegate : privateTrustStoreDelegates) {
            try {
                delegate.checkServerTrusted(chain, s);
            } catch (CertificateException e) {
                return e;
            }
        }
        return null;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String s) throws CertificateException {
        throw new CertificateException("This trust manager is only for clients");
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String s) throws CertificateException {
        // Check pins first, if certificate public key pinning is in use
        if (pinnedPublicKeys != null && pinnedPublicKeys.size() > 0) {
            boolean sawPin = false;
            for (X509Certificate cert : chain) {
                if (pinnedPublicKeys.contains(PublicKeyHash.fromPublicKey(cert.getPublicKey()))) {
                    sawPin = true;
                    break;
                }
            }
            if (!sawPin)
                throw new CertificateException("Server certificate chain did not contain any of the pinned public keys");
        }

        // Check private trust store first
        CertificateException e = checkPrivateTrustStoreDelegates(chain, s);
        if (e == null) {
            // We are done (succeeded)
            return;
        }

        // If we aren't trusting public PKI, we are done (failed)
        if (publicPkiDelegates == null || publicPkiDelegates.isEmpty())
            throw e;

        // All public PKI delegates must succeed
        for (X509TrustManager delegate : publicPkiDelegates) {
            delegate.checkServerTrusted(chain, s);
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
