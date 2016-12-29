/*
 * Copyright (c) 2016 CA. All rights reserved.
 *
 * This software may be modified and distributed under the terms
 * of the MIT license.  See the LICENSE file for details.
 *
 */

package com.ca.mas.core.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.security.KeyPairGeneratorSpec;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.RSAKeyGenParameterSpec;
import java.util.Calendar;
import java.util.Date;
import java.util.Enumeration;

import javax.security.auth.x500.X500Principal;

import sun.security.pkcs.PKCS10;
import sun.security.x509.X500Signer;

import static android.security.keystore.KeyProperties.BLOCK_MODE_CBC;
import static android.security.keystore.KeyProperties.BLOCK_MODE_CTR;
import static android.security.keystore.KeyProperties.BLOCK_MODE_ECB;
import static android.security.keystore.KeyProperties.BLOCK_MODE_GCM;
import static android.security.keystore.KeyProperties.DIGEST_MD5;
import static android.security.keystore.KeyProperties.DIGEST_NONE;
import static android.security.keystore.KeyProperties.DIGEST_SHA1;
import static android.security.keystore.KeyProperties.DIGEST_SHA256;
import static android.security.keystore.KeyProperties.DIGEST_SHA384;
import static android.security.keystore.KeyProperties.DIGEST_SHA512;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_PKCS7;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_RSA_OAEP;
import static android.security.keystore.KeyProperties.ENCRYPTION_PADDING_RSA_PKCS1;
import static android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PKCS1;
import static android.security.keystore.KeyProperties.SIGNATURE_PADDING_RSA_PSS;
import static com.ca.mas.core.MAG.DEBUG;
import static com.ca.mas.core.MAG.TAG;


/**
 * Utility methods for working with keys and key pairs.
 */
public class KeyUtils {

    private static final String ANDROID_KEY_STORE = "AndroidKeyStore";
    private static final int MAX_CHAIN = 9;

    /**
     * Generate a new RSA keypair with the specified number of key bits.
     *     This will always create the key pair inside the AndroidKeyStore,
     *        ensuring it is always protected.
     *
     * @param context needed for generating key pre-M
     * @param keysize the key size in bits, eg 2048.
     * @param alias the keystore alias to use
     * @return a new RSA PrivateKey.
     * @throws RuntimeException if an RSA key pair of the requested size cannot be generated
     */
    public static PrivateKey generateRsaPrivateKey(Context context, int keysize, String alias)
            throws java.security.InvalidAlgorithmParameterException, java.io.IOException,
            java.security.KeyStoreException, java.security.NoSuchAlgorithmException,
            java.security.NoSuchProviderException, java.security.cert.CertificateException,
            java.security.UnrecoverableKeyException
    {
        // use a minimum of 2048
        if (keysize < 2048)
            keysize = 2048;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // use KeyGenParameterSpec.Builder, new in Marshmallow
            return generateRsaPrivateKeyAndroidM(keysize, alias);
        }

        // For Android Pre-M
        // use the KeyPairGeneratorSpec.Builder, deprecated as of Marshmallow
        //    generates the key inside the AndroidKeyStore, always protected
        RSAKeyGenParameterSpec spec = new RSAKeyGenParameterSpec(keysize, RSAKeyGenParameterSpec.F4);
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", ANDROID_KEY_STORE);

        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        cal.add(Calendar.YEAR, 1);
        Date end = cal.getTime();

        kpg.initialize(new KeyPairGeneratorSpec.Builder(context)
                    .setAlias(alias)
                    .setAlgorithmParameterSpec(spec)
                    .setStartDate(now).setEndDate(end)
                    .setSerialNumber(BigInteger.valueOf(1))
                    .setSubject(new X500Principal("CN=msso"))
                    .build());
        return kpg.generateKeyPair().getPrivate();
    }

    /**
     * This method generates an RSA asymmetric key pair directly in
     *    the AndroidKeyStore.
     *
     * @param keysize the key size in bits, eg 2048.
     * @param alias the alias against which to store the key against
     * @return a new RSA keypair, created in and protected by the AndroidKeyStore, with an
     *        unusable self-signed certificate
     */
    @TargetApi(Build.VERSION_CODES.M)
    protected static PrivateKey generateRsaPrivateKeyAndroidM(int keysize, String alias)
            throws java.security.InvalidAlgorithmParameterException, java.io.IOException,
            java.security.KeyStoreException, java.security.NoSuchAlgorithmException,
            java.security.NoSuchProviderException, java.security.cert.CertificateException,
            java.security.UnrecoverableKeyException
    {
        /*
        try {
                KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE);
        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        cal.add(Calendar.YEAR, 1);
        Date end = cal.getTime();
        //NOTE: setUserAuthenticationRequired does not work correctly if fingerprints are enabled
        keyPairGenerator.initialize(
                new KeyGenParameterSpec.Builder(alias + "_test", KeyProperties.PURPOSE_ENCRYPT + KeyProperties.PURPOSE_DECRYPT + KeyProperties.PURPOSE_SIGN + KeyProperties.PURPOSE_VERIFY)
                        .setKeySize(keysize)
                        .setCertificateNotBefore(now).setCertificateNotAfter(end)
                        .setCertificateSubject(new X500Principal("CN=msso, ou=test"))
                        .setCertificateSerialNumber(BigInteger.valueOf(1))
                        //test
                        .setUserAuthenticationRequired(true)
                        // In HttpUrlConnection, com.android.org.conscrypt.CryptoUpcalls.rawSignDigestWithPrivateKey
                        //   requires "NONEwithRSA", so we need to include DIGEST_NONE here
                        //.setRandomizedEncryptionRequired(true)
                        .setRandomizedEncryptionRequired(false)
                        .setBlockModes(BLOCK_MODE_CBC, BLOCK_MODE_CTR, BLOCK_MODE_ECB, BLOCK_MODE_GCM)
                        // In HttpUrlConnection, com.android.org.conscrypt.CryptoUpcalls.rawSignDigestWithPrivateKey
                        //   requires "NONEwithRSA", so we need to include DIGEST_NONE here
                        .setDigests(DIGEST_NONE, DIGEST_MD5, DIGEST_SHA1, DIGEST_SHA256, DIGEST_SHA384, DIGEST_SHA512)
                        .setEncryptionPaddings(ENCRYPTION_PADDING_PKCS7, ENCRYPTION_PADDING_RSA_OAEP, ENCRYPTION_PADDING_RSA_PKCS1)
                        .setSignaturePaddings(SIGNATURE_PADDING_RSA_PSS, SIGNATURE_PADDING_RSA_PKCS1)
                        .build());

            PrivateKey test = keyPairGenerator.generateKeyPair().getPrivate();
            Log.i("CERTIFICATE PROV", "generateRSAPrivateKey generated test, " + test);
        }catch (Exception x) {
            Log.i("CERTIFICATE PROV", "generateRSAPrivateKey generating test EXCEPTION: " + x);
        }
        */

        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_RSA, ANDROID_KEY_STORE);
        Calendar cal = Calendar.getInstance();
        Date now = cal.getTime();
        cal.add(Calendar.YEAR, 1);
        Date end = cal.getTime();
        keyPairGenerator.initialize(
                new KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT + KeyProperties.PURPOSE_DECRYPT + KeyProperties.PURPOSE_SIGN + KeyProperties.PURPOSE_VERIFY)
                        .setKeySize(keysize)
                        .setCertificateNotBefore(now).setCertificateNotAfter(end)
                        .setCertificateSubject(new X500Principal("CN=msso"))
                        .setCertificateSerialNumber(BigInteger.valueOf(1))
                        .setUserAuthenticationRequired(false)
                        // In HttpUrlConnection, com.android.org.conscrypt.CryptoUpcalls.rawSignDigestWithPrivateKey
                        //   requires "NONEwithRSA", so we need to include DIGEST_NONE here
                        //.setRandomizedEncryptionRequired(true)
                        .setRandomizedEncryptionRequired(false)
                        .setBlockModes(BLOCK_MODE_CBC, BLOCK_MODE_CTR, BLOCK_MODE_ECB, BLOCK_MODE_GCM)
                        // In HttpUrlConnection, com.android.org.conscrypt.CryptoUpcalls.rawSignDigestWithPrivateKey
                        //   requires "NONEwithRSA", so we need to include DIGEST_NONE here
                        .setDigests(DIGEST_NONE, DIGEST_MD5, DIGEST_SHA1, DIGEST_SHA256, DIGEST_SHA384, DIGEST_SHA512)
                        .setEncryptionPaddings(ENCRYPTION_PADDING_PKCS7, ENCRYPTION_PADDING_RSA_OAEP, ENCRYPTION_PADDING_RSA_PKCS1)
                        .setSignaturePaddings(SIGNATURE_PADDING_RSA_PSS, SIGNATURE_PADDING_RSA_PKCS1)
                        .build());
        return keyPairGenerator.generateKeyPair().getPrivate();
    }

    /**
     * Get the existing private key.
     *     Note: the initial self-signed public cert is not usable.
     *
     * @param alias the alias of the existing private key
     * @return the Private Key object
     */
    public static PrivateKey getRsaPrivateKey(String alias)
            throws java.io.IOException, java.security.KeyStoreException,
            java.security.NoSuchAlgorithmException, java.security.cert.CertificateException,
            java.security.UnrecoverableKeyException
    {
        Log.i("CERTIFICATE PROV", "Getting the test private key, alias " + alias);
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        for (Enumeration e=keyStore.aliases(); e.hasMoreElements(); ) {
            String aliasFound = (String) e.nextElement();
            Log.i("CERTIFICATE PROV", "Getting the test private key, found alias " + aliasFound);
        }
        try {
            PrivateKey privateKey = (PrivateKey) keyStore.getKey(alias + "_test", null);
            Log.i("CERTIFICATE PROV", "Got the test private key");
            Certificate cert = keyStore.getCertificate(alias + "_test");
            PublicKey publicKey = cert.getPublicKey();
            Log.i("CERTIFICATE PROV", "Got the test public key");

            PKCS10 pkcs10 = new PKCS10(publicKey);
            Signature signature = Signature.getInstance("SHA256withRSA");
            Log.i("CERTIFICATE PROV", "Got the test signature initSign before");
            signature.initSign(privateKey);
            Log.i("CERTIFICATE PROV", "Got the test signature initSign after");
            sun.security.x509.X500Name x500Name = new sun.security.x509.X500Name("cn=abc, ou=def, dc=ghi, o=o");

            pkcs10.encodeAndSign(new X500Signer(signature, x500Name));
            Log.i("CERTIFICATE PROV", "Got the test signature encodeAndSign done");
            byte bytes[] = pkcs10.getEncoded();
            Log.i("CERTIFICATE PROV", "Got the test private key and signed with it!!!!");
        } catch (Exception e) {
            Log.i("CERTIFICATE PROV", "Got the test private key and signed with it EXCEPTION: " + e);
            e.printStackTrace();
        } catch (Throwable t) {
            Log.i("CERTIFICATE PROV", "Got the test private key and signed with it THROWABLE: " + t);
            t.printStackTrace();
        }/* */

        //KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        //keyStore.load(null);
        return (PrivateKey) keyStore.getKey(alias, null);
    }


    /**
     * Get the existing public key.
     *     Note: the private key will be associated with the initial
     *     self-signed public certificate.  The PublicKey can be
     *     used in a Certificate Signing Request.
     *
     * @param alias the alias of the existing private key
     * @return the Private Key object
     */
    public static PublicKey getRsaPublicKey(String alias)
            throws java.io.IOException, java.security.KeyStoreException,
            java.security.NoSuchAlgorithmException, java.security.cert.CertificateException
    {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
        keyStore.load(null);
        Certificate cert = keyStore.getCertificate(alias);
        if (cert != null)
            return cert.getPublicKey();
        else
            return null;
    }


    /**
     * Remove the existing public key.
     *     Note: the initial self-signed public cert is not usable.
     *
     * @param alias the alias of the existing private key +
     *              self-signed public key
     * @return the Private Key object
     */
    public static void deletePrivateKey(String alias)
    {
        try {
            KeyStore keyStore = KeyStore.getInstance(ANDROID_KEY_STORE);
            keyStore.load(null);
            keyStore.deleteEntry(alias);
        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "Unable delete privage key: " + e.getMessage(), e);
        }
    }


    /**
     * This will install or replace the existing certificate chain in the AndroidKeyStore.
     *       The chain will be stored as "alias#".
     * @param aliasPrefix the alias prefix to use, will be appended with position number,
     *                    where position 0 in the array will be 1.
     * @param chain array of certificates in chain.  Typically the public certificate
     *              matching the private key will be in array position [0].
     */
    public static void setCertificateChain(String aliasPrefix, X509Certificate chain[])
            throws java.io.IOException, java.security.KeyStoreException,
            java.security.NoSuchAlgorithmException, java.security.cert.CertificateException
    {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        // delete any existing ones
        for (int i=1; i<=MAX_CHAIN; i++) {
            keyStore.deleteEntry(aliasPrefix + i);
        }
        // now add the new ones
        for (int i=0; i<chain.length; i++) {
            keyStore.setCertificateEntry(aliasPrefix + (i+1), chain[i]);
        }
    }

    /**
     * Clear any existing certificates in the public certificate chain.
     * @param aliasPrefix
     * @return
     */
    public static X509Certificate[] getCertificateChain(String aliasPrefix)
            throws java.io.IOException, java.security.KeyStoreException,
            java.security.NoSuchAlgorithmException, java.security.cert.CertificateException
    {
        X509Certificate returnChain[] = null;

        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        // discover how many certificates are in the chain
        int numInChain = 0;
        for (Enumeration e = keyStore.aliases(); e.hasMoreElements(); ) {
            String aliasFound = (String) e.nextElement();
            if (aliasFound.startsWith(aliasPrefix)) {
                int numAlias = Integer.parseInt(aliasFound.replace(aliasPrefix, ""));
                if (numAlias > numInChain)
                    numInChain = numAlias;
            }
        }
        if (numInChain > 0) {
            returnChain = new X509Certificate[numInChain];
            for (int i=0; i<numInChain; i++)
                returnChain[i] = (X509Certificate) keyStore.getCertificate(aliasPrefix + (i+1));
        }

        return returnChain;
    }

    /**
     * This will install or replace the existing certificate chain in the AndroidKeyStore.
     *       The chain will be stored as "alias#".
     * @param aliasPrefix the alias prefix to use, will be appended with position number,
     *                    where position 0 in the array will be 1.
     */
    public static void clearCertificateChain(String aliasPrefix)
    {
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            // delete any existing ones
            for (int i = 1; i <= MAX_CHAIN; i++) {
                keyStore.deleteEntry(aliasPrefix + i);
            }
        } catch (Exception e) {
            if (DEBUG) Log.e(TAG, "Unable to clear certificate chain: " + e.getMessage(), e);
        }
    }

    private KeyUtils() {
    }
}
