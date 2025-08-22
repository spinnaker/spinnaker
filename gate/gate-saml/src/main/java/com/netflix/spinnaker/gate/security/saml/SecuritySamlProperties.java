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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;
import javax.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties("saml")
@NullableByDefault
public class SecuritySamlProperties {
  public static final String FILE_PREFIX = "file:";
  private Path keyStore;
  private String keyStoreType = "PKCS12";
  private String keyStorePassword;
  private String keyStoreAliasName = "mykey"; // default alias for keytool

  // the privatekey/cert location files can be generated via
  // openssl req -new -x509 -nodes -keyout private_key.pem -out certificate.pem -subj
  // "/CN=Spinnaker" -days 3650
  @Data
  @NoArgsConstructor
  public static class Credential {
    private String privateKeyLocation;
    private String certificateLocation;

    public Credential(String privateKeyLocation, String certificateLocation) {
      setCertificateLocation(certificateLocation);
      setPrivateKeyLocation(privateKeyLocation);
    }

    public void setCertificateLocation(String certificateLocation) {
      this.certificateLocation = addFilePrefixIfNeeded(certificateLocation);
    }

    public void setPrivateKeyLocation(String privateKeyLocation) {
      this.privateKeyLocation = addFilePrefixIfNeeded(privateKeyLocation);
    }
  }

  private List<Credential> signingCredentials = new LinkedList<>();

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

  private static String addFilePrefixIfNeeded(String property) {
    if (StringUtils.hasLength(property) && property.startsWith("/")) {
      return FILE_PREFIX + property;
    }
    return property;
  }

  // @formatter:off
  /**
   * Try to match the standard spring saml config LIKE the below. This would be a keylcoak
   * configuration example. Note that this does NOT match, but at least the private key suff does. *
   * <br>
   * <!-- @formatter:off -->
   *
   * <pre>
   * spring:
   *   security:
   *     saml2:
   *     ## We ARE a relying party.
   *       relyingparty:
   *         registration:
   *           SSO:
   *           ## This would be the SSO provider information.  Asserting party would be who's sending the SAML payload (e.g. okta/keycloak/etc)
   *             assertingparty:
   *               metadata-uri: http://192.168.1.2:8080/realms/master/protocol/saml/descriptor
   *               entityId: Spinnaker
   *               ## This uses the signing credentials in a separate block.  Why it's there vs. here is strange
   *               singlesignon:
   *                 sign-request: true
   *                 url: http://192.168.1.2:8080/realms/master/protocol/saml
   *               ## This IF metadata-uri is set SHOULD NOT be needed as it's PARSED as part of the operation
   *               ## this is the decryptionCredentials
   *               verification:
   *               ## Nominally this could be in a JKS or a P12 keystore as well.  REMINDER:  this is basically
   *               ## coming from the metadata in NORMAL cases.
   *                 credentials:
   *                 - certificate-location: encryptedFile:k8s!n:saml-verification-secret!k:certificate.pem
   *                   private-key-location: encryptedFile:k8s!n:saml-verification-secret!k:private_key.pem
   *             signing:
   *               credentials:
   *               - certificate-location: encryptedFile:k8s!n:saml-signing-secret!k:certificate.pem
   *                 private-key-location: encryptedFile:k8s!n:saml-signing-secret!k:private_key.pem
   *
   * </pre>
   *
   * *
   * <!-- @formatter:on -->
   * NOTE: We ONLY support a single credential with this config.
   *
   * @return the credentials piece based upon privateKey/certificate
   */
  // @formatter:on
  // TODO:  Replace this entire thing with standard spring security saml loading vs. doing all this
  // ourselves
  // See:
  // https://docs.spring.io/spring-boot/api/java/org/springframework/boot/autoconfigure/security/saml2/Saml2RelyingPartyProperties.Registration.html for the auto wiring based approach
  @Nonnull
  public List<Saml2X509Credential> getSigningCredentials() {
    if (this.signingCredentials != null && !signingCredentials.isEmpty()) {
      return this.signingCredentials.stream()
          .map(
              each ->
                  getSaml2Credential(
                      each.getPrivateKeyLocation(),
                      each.getCertificateLocation(),
                      Saml2X509Credential.Saml2X509CredentialType.SIGNING))
          .toList();
    }
    return new LinkedList<>();
  }

  /**
   * Sign requests via the WantAuthnRequestsSigned XML flag. Defaults to false. Keycloak defaults to
   * true, okta DOES NOT support true. Uses the signing credentials
   */
  private boolean signRequests = false;

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
    metadataUrl = addFilePrefixIfNeeded(metadataUrl);
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

  /// Filched DIRECTLY from spring-security private temporarily until all of this is replaced
  // directly with the autowired spring configuration stuff
  ///
  // https://github.com/spring-projects/spring-security/blob/main/config/src/main/java/org/springframework/security/config/saml2/RelyingPartyRegistrationsBeanDefinitionParser.java
  private static final ResourceLoader resourceLoader = new DefaultResourceLoader();

  private static Saml2X509Credential getSaml2Credential(
      String privateKeyLocation,
      String certificateLocation,
      Saml2X509Credential.Saml2X509CredentialType credentialType) {
    RSAPrivateKey privateKey = readPrivateKey(privateKeyLocation);
    X509Certificate certificate = readCertificate(certificateLocation);
    return new Saml2X509Credential(privateKey, certificate, credentialType);
  }

  private static RSAPrivateKey readPrivateKey(String privateKeyLocation) {
    Resource privateKey = resourceLoader.getResource(privateKeyLocation);
    try (InputStream inputStream = privateKey.getInputStream()) {
      return RsaKeyConverters.pkcs8().convert(inputStream);
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  private static X509Certificate readCertificate(String certificateLocation) {
    Resource certificate = resourceLoader.getResource(certificateLocation);
    try (InputStream inputStream = certificate.getInputStream()) {
      return (X509Certificate)
          CertificateFactory.getInstance("X.509").generateCertificate(inputStream);
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }
}
