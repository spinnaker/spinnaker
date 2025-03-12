/*
 * Copyright 2023 Apple Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.crypto.test;

import com.netflix.spinnaker.kork.crypto.NestedSecurityIOException;
import com.netflix.spinnaker.kork.crypto.NestedSecurityRuntimeException;
import com.netflix.spinnaker.kork.crypto.StandardCrypto;
import com.netflix.spinnaker.kork.crypto.X509Identity;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import javax.security.auth.x500.X500PrivateCredential;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;

/**
 * Utilities for generating root certificate authorities and signing certification requests.
 *
 * @see <a href="https://stackoverflow.com/a/44981616">generating a self-signed certificate</a>
 * @see org.bouncycastle.pkcs.PKCS10CertificationRequestBuilder
 * @see org.bouncycastle.asn1.x509.ExtensionsGenerator
 */
public class CertificateIdentity {
  /** Distinguished name for a test root certificate authority. */
  public static final X500Name ISSUER = new X500Name("CN=Test Certificate Authority");

  private final X500PrivateCredential credential;
  private final ContentSigner signer;

  private CertificateIdentity(X500PrivateCredential credential) {
    this.credential = credential;
    signer = signerFrom(credential.getPrivateKey());
  }

  /**
   * Saves this certificate identity as a PKCS#12-encoded keystore file with the provided password.
   * Both the keystore and this identity's private key will be protected with the provided password.
   */
  public void saveAsPKCS12(Path keystoreFile, char[] password) throws IOException {
    var entry =
        new KeyStore.PrivateKeyEntry(getPrivateKey(), new X509Certificate[] {getCertificate()});
    var protectionParameter = new KeyStore.PasswordProtection(password);
    var keyStore = StandardCrypto.getPKCS12KeyStore();
    try (var output = Files.newOutputStream(keystoreFile)) {
      keyStore.load(null, null);
      keyStore.setEntry(getAlias(), entry, protectionParameter);
      keyStore.store(output, password);
    } catch (GeneralSecurityException e) {
      throw new NestedSecurityIOException(e);
    }
  }

  /**
   * Saves this certificate identity as a pair of PEM-encoded private key and certificate files. The
   * private key is not password-protected.
   */
  public void saveAsPEM(Path keyFile, Path certificateFile) throws IOException {
    Base64.Encoder encoder = Base64.getEncoder();
    try (var writer = Files.newBufferedWriter(keyFile)) {
      writer.write("-----BEGIN PRIVATE KEY-----");
      writer.newLine();
      writer.write(encoder.encodeToString(getPrivateKey().getEncoded()));
      writer.newLine();
      writer.write("-----END PRIVATE KEY-----");
      writer.newLine();
    }
    try (var writer = Files.newBufferedWriter(certificateFile)) {
      writer.write("-----BEGIN CERTIFICATE-----");
      writer.newLine();
      writer.write(encoder.encodeToString(getCertificate().getEncoded()));
      writer.newLine();
      writer.write("-----END CERTIFICATE-----");
      writer.newLine();
    } catch (CertificateEncodingException e) {
      throw new NestedSecurityIOException(e);
    }
  }

  /**
   * Signs the given certification request using this certificate identity. Returns a new X.509
   * certificate valid for an hour including all the requested extensions.
   */
  public X509Certificate signCertificationRequest(PKCS10CertificationRequest request)
      throws IOException {
    BigInteger serial = generateSerial();
    Date notBefore = new Date();
    Date notAfter = Date.from(notBefore.toInstant().plus(Duration.ofHours(1)));
    var builder =
        new X509v3CertificateBuilder(
            ISSUER,
            serial,
            notBefore,
            notAfter,
            request.getSubject(),
            request.getSubjectPublicKeyInfo());

    // next, copy all requested extensions (we'll pretend to support them all)
    var extensions = request.getRequestedExtensions();
    for (ASN1ObjectIdentifier oid : extensions.getCriticalExtensionOIDs()) {
      builder.addExtension(oid, true, extensions.getExtensionParsedValue(oid));
    }
    for (ASN1ObjectIdentifier oid : extensions.getNonCriticalExtensionOIDs()) {
      builder.addExtension(oid, false, extensions.getExtensionParsedValue(oid));
    }

    X509CertificateHolder certificateHolder = builder.build(signer);
    return convertFromBC(certificateHolder);
  }

  public X509Certificate getCertificate() {
    return credential.getCertificate();
  }

  public PrivateKey getPrivateKey() {
    return credential.getPrivateKey();
  }

  public String getAlias() {
    return credential.getAlias();
  }

  public static CertificateIdentity generateSelfSigned() throws IOException {
    var keyPair = TestCrypto.generateKeyPair();
    var privateKey = keyPair.getPrivate();
    var signer = signerFrom(privateKey);

    // set up a certificate authority
    X509CertificateHolder certificateHolder =
        builderForPublicKey(keyPair.getPublic())
            // specify key usage to allow certificate signing as a CA cert
            .addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign))
            // specify this certificate is a CA cert (a leaf certificate)
            .addExtension(Extension.basicConstraints, true, new BasicConstraints(true))
            .build(signer);
    var certificate = convertFromBC(certificateHolder);
    return fromCredentials(privateKey, certificate);
  }

  public static ContentSigner signerFrom(PrivateKey privateKey) {
    try {
      return new JcaContentSignerBuilder("SHA256withECDSA").build(privateKey);
    } catch (OperatorCreationException e) {
      Throwable cause = e.getCause();
      throw cause instanceof GeneralSecurityException
          ? new NestedSecurityRuntimeException((GeneralSecurityException) cause)
          : new IllegalArgumentException(e);
    }
  }

  public static CertificateIdentity fromCredentials(
      PrivateKey privateKey, X509Certificate certificate) {
    var alias = X509Identity.generateAlias(certificate);
    var credential = new X500PrivateCredential(certificate, privateKey, alias);
    return new CertificateIdentity(credential);
  }

  private static X509v3CertificateBuilder builderForPublicKey(PublicKey publicKey) {
    BigInteger serial = generateSerial();
    // use a validity range of now to one year from now
    Date notBefore = new Date();
    Date notAfter = Date.from(notBefore.toInstant().plus(Duration.ofDays(1)));
    return new JcaX509v3CertificateBuilder(ISSUER, serial, notBefore, notAfter, ISSUER, publicKey);
  }

  private static BigInteger generateSerial() {
    // ensure we use somewhat unique serial numbers
    return BigInteger.valueOf(System.currentTimeMillis());
  }

  private static X509Certificate convertFromBC(X509CertificateHolder certificateHolder)
      throws IOException {
    // convert BC X.509 certificate into a standard Java X509Certificate
    CertificateFactory certificateFactory = StandardCrypto.getX509CertificateFactory();
    try {
      return (X509Certificate)
          certificateFactory.generateCertificate(
              new ByteArrayInputStream(certificateHolder.getEncoded()));
    } catch (CertificateException e) {
      throw new NestedSecurityIOException(e);
    }
  }
}
