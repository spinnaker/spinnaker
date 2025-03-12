/*
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
 *
 */

package com.netflix.spinnaker.gate.security.saml;

import com.netflix.spinnaker.kork.annotations.NullableByDefault;
import com.netflix.spinnaker.kork.exceptions.ConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("saml")
@NullableByDefault
public class SecuritySamlProperties {
  private Path keyStore;
  private String keyStoreType = "PKCS12";
  private String keyStorePassword;
  private String keyStoreAliasName = "mykey"; // default alias for keytool

  public Saml2X509Credential getDecryptionCredential()
      throws IOException, GeneralSecurityException {
    if (keyStore == null) {
      return null;
    }
    if (keyStoreType == null) {
      keyStoreType = "PKCS12";
    }
    KeyStore store = KeyStore.getInstance(keyStoreType);
    char[] password = keyStorePassword != null ? keyStorePassword.toCharArray() : new char[0];
    try (var stream = Files.newInputStream(keyStore)) {
      store.load(stream, password);
    }
    String alias = keyStoreAliasName;
    var certificate = (X509Certificate) store.getCertificate(alias);
    var privateKey = (PrivateKey) store.getKey(alias, password);
    return Saml2X509Credential.decryption(privateKey, certificate);
  }

  /** URL pointing to the SAML metadata to use. */
  private String metadataUrl;

  /** Registration id for this SAML provider. Used in SAML processing URLs. */
  @NotEmpty private String registrationId = "SSO";

  /**
   * The Relying Party's entity ID (sometimes called an issuer ID). The value may contain a number
   * of placeholders. They are "baseUrl", "registrationId", "baseScheme", "baseHost", and
   * "basePort".
   */
  @NotEmpty private String issuerId = "{baseUrl}/saml2/metadata";

  /**
   * The path used for login processing. When combined with the base URL, this should form the
   * assertion consumer service location.
   */
  @NotEmpty private String loginProcessingUrl = "/saml/{registrationId}";

  /**
   * Returns the assertion consumer service location template to use for redirecting back from the
   * identity provider.
   */
  public String getAssertionConsumerServiceLocation() {
    return "{baseUrl}" + loginProcessingUrl;
  }

  /** Optional list of roles required for authentication to succeed. */
  private List<String> requiredRoles;

  /** Determines whether to sort the roles returned from the SAML provider. */
  private boolean sortRoles = false;

  /** Toggles whether role names should be converted to lowercase. */
  private boolean forceLowercaseRoles = true;

  @Nonnull @NestedConfigurationProperty
  private UserAttributeMapping userAttributeMapping = new UserAttributeMapping();

  @PostConstruct
  public void validate() throws IOException, GeneralSecurityException {
    if (StringUtils.hasLength(metadataUrl) && metadataUrl.startsWith("/")) {
      metadataUrl = "file:" + metadataUrl;
    }
    if (keyStore != null) {
      if (keyStoreType == null) {
        keyStoreType = "PKCS12";
      }
      var keystore = KeyStore.getInstance(keyStoreType);
      var password = keyStorePassword != null ? keyStorePassword.toCharArray() : new char[0];
      try (var stream = Files.newInputStream(keyStore)) {
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
  }

  @Nonnull
  private static Set<String> caseInsensitiveSetFromAliasEnumeration(
      @Nonnull Enumeration<String> enumeration) {
    Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    while (enumeration.hasMoreElements()) {
      set.add(enumeration.nextElement());
    }
    return set;
  }

  @Getter
  @Setter
  @Validated
  public static class UserAttributeMapping {
    @NotEmpty private String firstName = "User.FirstName";
    @NotEmpty private String lastName = "User.LastName";
    @NotEmpty private String roles = "memberOf";
    @NotEmpty private String rolesDelimiter = ";";
    private String username;
    private String email;
  }
}
