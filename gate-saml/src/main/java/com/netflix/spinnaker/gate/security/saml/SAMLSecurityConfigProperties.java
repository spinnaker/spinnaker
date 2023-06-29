/*
 * Copyright 2014 Netflix, Inc.
 * Copyright 2023 Apple, Inc.
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

package com.netflix.spinnaker.gate.security.saml;

import com.netflix.spinnaker.kork.exceptions.ConfigurationException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.PostConstruct;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.SignatureMethod;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.opensaml.xml.signature.SignatureConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.util.StringUtils;

@Getter
@Setter
@ConfigurationProperties("saml")
public class SAMLSecurityConfigProperties {
  private static final String FILE_SCHEME = "file:";

  private String keyStore;
  private String keyStoreType = "PKCS12";
  private String keyStorePassword;
  private String keyStoreAliasName;

  // SAML DSL uses a metadata URL instead of hard coding a certificate/issuerId/redirectBase into
  // the config.
  private String metadataUrl;
  // The parts of this endpoint passed to/used by the SAML IdP.
  private String redirectProtocol = "https";
  private String redirectHostname;
  private String redirectBasePath = "/";
  // The application identifier given to the IdP for this app.
  private String issuerId;

  private List<String> requiredRoles;
  private boolean sortRoles = false;
  private boolean forceLowercaseRoles = true;

  @NestedConfigurationProperty
  private UserAttributeMapping userAttributeMapping = new UserAttributeMapping();

  private long maxAuthenticationAge = 7200;

  // SHA1 is the default registered in DefaultSecurityConfigurationBootstrap.populateSignatureParams
  private String signatureDigest = "SHA1";

  public SignatureDigest signatureDigest() {
    return SignatureDigest.fromName(signatureDigest);
  }

  /**
   * Ensure that the keystore exists and can be accessed with the given keyStorePassword and
   * keyStoreAliasName. Validates the configured signature/digest is supported.
   */
  @PostConstruct
  public void validate() throws IOException, GeneralSecurityException {
    if (StringUtils.hasLength(metadataUrl) && metadataUrl.startsWith("/")) {
      metadataUrl = FILE_SCHEME + metadataUrl;
    }
    if (StringUtils.hasLength(keyStore)) {
      if (!keyStore.startsWith(FILE_SCHEME)) {
        keyStore = FILE_SCHEME + keyStore;
      }
      var path = Path.of(URI.create(keyStore));
      var keystore = KeyStore.getInstance(keyStoreType);
      var password = keyStorePassword != null ? keyStorePassword.toCharArray() : new char[0];
      try (var stream = Files.newInputStream(path)) {
        // will throw an exception if `keyStorePassword` is invalid or if the key store file is
        // invalid
        keystore.load(stream, password);
      }
      if (StringUtils.hasLength(keyStoreAliasName)) {
        var aliases = caseInsensitiveSetFromAliasEnumeration(keystore.aliases());
        if (!aliases.contains(keyStoreAliasName)) {
          throw new ConfigurationException(
              String.format(
                  "Keystore '%s' does not contain alias '%s'; found aliases: %s",
                  keyStore, keyStoreAliasName, aliases));
        }
      }
    }
    // Validate signature digest algorithm
    Objects.requireNonNull(signatureDigest());
  }

  private static Set<String> caseInsensitiveSetFromAliasEnumeration(
      Enumeration<String> enumeration) {
    Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    while (enumeration.hasMoreElements()) {
      set.add(enumeration.nextElement());
    }
    return set;
  }

  @Getter
  @Setter
  public static class UserAttributeMapping {
    private String firstName = "User.FirstName";
    private String lastName = "User.LastName";
    private String roles = "memberOf";
    private String rolesDelimiter = ";";
    private String username;
    private String email;
  }

  // only RSA-based signatures explicitly supported here (baseline requirement for XML signatures)
  @Getter
  @RequiredArgsConstructor
  public enum SignatureDigest {
    @Deprecated
    SHA1(SignatureMethod.RSA_SHA1, DigestMethod.SHA1),
    SHA256(SignatureMethod.RSA_SHA256, DigestMethod.SHA256),
    SHA384(SignatureMethod.RSA_SHA384, DigestMethod.SHA384),
    SHA512(SignatureMethod.RSA_SHA512, DigestMethod.SHA512),
    @Deprecated
    RIPEMD160(SignatureConstants.ALGO_ID_SIGNATURE_RSA_RIPEMD160, DigestMethod.RIPEMD160),
    @Deprecated
    MD5(
        SignatureConstants.ALGO_ID_SIGNATURE_NOT_RECOMMENDED_RSA_MD5,
        SignatureConstants.ALGO_ID_DIGEST_NOT_RECOMMENDED_MD5),
    ;
    private final String signatureMethod;
    private final String digestMethod;

    public static SignatureDigest fromName(String name) {
      return valueOf(name.toUpperCase(Locale.ROOT));
    }
  }
}
