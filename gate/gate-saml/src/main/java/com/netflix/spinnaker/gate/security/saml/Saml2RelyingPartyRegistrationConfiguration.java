/*
 * Copyright 2026 Harness, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.security.saml;

import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.ssl.pem.PemContent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.core.Saml2X509Credential.Saml2X509CredentialType;
import org.springframework.security.saml2.provider.service.registration.AssertingPartyMetadata;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration.Builder;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Maps {@link Saml2RelyingPartyProperties} to relying party registrations.
 *
 * <p>Faithful port of Spring Boot 3's {@code Saml2RelyingPartyRegistrationConfiguration}, which
 * Boot 4 removed along with its SAML auto-configuration. Preserves the Boot 3 operator contract
 * ({@code spring.security.saml2.relyingparty.registration.*}) on Boot 4 / Spring Security 7.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(Saml2RelyingPartyRegistrationConfiguration.RegistrationConfiguredCondition.class)
@ConditionalOnMissingBean(RelyingPartyRegistrationRepository.class)
@EnableConfigurationProperties(Saml2RelyingPartyProperties.class)
class Saml2RelyingPartyRegistrationConfiguration {

  @Bean
  RelyingPartyRegistrationRepository relyingPartyRegistrationRepository(
      Saml2RelyingPartyProperties properties) {
    List<RelyingPartyRegistration> registrations =
        properties.getRegistration().entrySet().stream().map(this::asRegistration).toList();
    return new InMemoryRelyingPartyRegistrationRepository(registrations);
  }

  private RelyingPartyRegistration asRegistration(
      Map.Entry<String, Saml2RelyingPartyProperties.Registration> entry) {
    return asRegistration(entry.getKey(), entry.getValue());
  }

  private RelyingPartyRegistration asRegistration(
      String id, Saml2RelyingPartyProperties.Registration properties) {
    boolean usingMetadata = StringUtils.hasText(properties.getAssertingparty().getMetadataUri());
    Builder builder =
        (!usingMetadata)
            ? RelyingPartyRegistration.withRegistrationId(id)
            : createBuilderUsingMetadata(properties.getAssertingparty()).registrationId(id);
    builder.assertionConsumerServiceLocation(properties.getAcs().getLocation());
    builder.assertionConsumerServiceBinding(properties.getAcs().getBinding());
    builder.assertingPartyMetadata(mapAssertingParty(properties.getAssertingparty()));
    builder.signingX509Credentials(
        (credentials) ->
            properties.getSigning().getCredentials().stream()
                .map(this::asSigningCredential)
                .forEach(credentials::add));
    builder.decryptionX509Credentials(
        (credentials) ->
            properties.getDecryption().getCredentials().stream()
                .map(this::asDecryptionCredential)
                .forEach(credentials::add));
    builder.assertingPartyMetadata(
        (details) ->
            details.verificationX509Credentials(
                (credentials) ->
                    properties.getAssertingparty().getVerification().getCredentials().stream()
                        .map(this::asVerificationCredential)
                        .forEach(credentials::add)));
    builder.singleLogoutServiceLocation(properties.getSinglelogout().getUrl());
    builder.singleLogoutServiceResponseLocation(properties.getSinglelogout().getResponseUrl());
    builder.singleLogoutServiceBinding(properties.getSinglelogout().getBinding());
    builder.entityId(properties.getEntityId());
    builder.nameIdFormat(properties.getNameIdFormat());
    RelyingPartyRegistration registration = builder.build();
    boolean signRequest = registration.getAssertingPartyMetadata().getWantAuthnRequestsSigned();
    validateSigningCredentials(properties, signRequest);
    return registration;
  }

  private RelyingPartyRegistration.Builder createBuilderUsingMetadata(
      Saml2RelyingPartyProperties.AssertingParty properties) {
    String requiredEntityId = properties.getEntityId();
    Collection<Builder> candidates =
        RelyingPartyRegistrations.collectionFromMetadataLocation(properties.getMetadataUri());
    for (RelyingPartyRegistration.Builder candidate : candidates) {
      if (requiredEntityId == null || requiredEntityId.equals(getEntityId(candidate))) {
        return candidate;
      }
    }
    throw new IllegalStateException(
        "No relying party with Entity ID '" + requiredEntityId + "' found");
  }

  private Object getEntityId(RelyingPartyRegistration.Builder candidate) {
    String[] result = new String[1];
    candidate.assertingPartyMetadata((builder) -> result[0] = builder.build().getEntityId());
    return result[0];
  }

  private Consumer<AssertingPartyMetadata.Builder<?>> mapAssertingParty(
      Saml2RelyingPartyProperties.AssertingParty assertingParty) {
    return (details) -> {
      // Boot 4's PropertyMapper dropped alwaysApplyingWhenNonNull(); apply-if-present inline.
      if (assertingParty.getEntityId() != null) {
        details.entityId(assertingParty.getEntityId());
      }
      if (assertingParty.getSinglesignon().getBinding() != null) {
        details.singleSignOnServiceBinding(assertingParty.getSinglesignon().getBinding());
      }
      if (assertingParty.getSinglesignon().getUrl() != null) {
        details.singleSignOnServiceLocation(assertingParty.getSinglesignon().getUrl());
      }
      if (assertingParty.getSinglesignon().getSignRequest() != null) {
        details.wantAuthnRequestsSigned(assertingParty.getSinglesignon().getSignRequest());
      }
      if (assertingParty.getSinglelogout().getUrl() != null) {
        details.singleLogoutServiceLocation(assertingParty.getSinglelogout().getUrl());
      }
      if (assertingParty.getSinglelogout().getResponseUrl() != null) {
        details.singleLogoutServiceResponseLocation(
            assertingParty.getSinglelogout().getResponseUrl());
      }
      if (assertingParty.getSinglelogout().getBinding() != null) {
        details.singleLogoutServiceBinding(assertingParty.getSinglelogout().getBinding());
      }
    };
  }

  private void validateSigningCredentials(
      Saml2RelyingPartyProperties.Registration properties, boolean signRequest) {
    if (signRequest) {
      Assert.state(
          !properties.getSigning().getCredentials().isEmpty(),
          "Signing credentials must not be empty when authentication requests require signing.");
    }
  }

  private Saml2X509Credential asSigningCredential(
      Saml2RelyingPartyProperties.Registration.Signing.Credential properties) {
    RSAPrivateKey privateKey = readPrivateKey(properties.getPrivateKeyLocation());
    X509Certificate certificate = readCertificate(properties.getCertificateLocation());
    return new Saml2X509Credential(privateKey, certificate, Saml2X509CredentialType.SIGNING);
  }

  private Saml2X509Credential asDecryptionCredential(
      Saml2RelyingPartyProperties.Decryption.Credential properties) {
    RSAPrivateKey privateKey = readPrivateKey(properties.getPrivateKeyLocation());
    X509Certificate certificate = readCertificate(properties.getCertificateLocation());
    return new Saml2X509Credential(privateKey, certificate, Saml2X509CredentialType.DECRYPTION);
  }

  private Saml2X509Credential asVerificationCredential(
      Saml2RelyingPartyProperties.AssertingParty.Verification.Credential properties) {
    X509Certificate certificate = readCertificate(properties.getCertificateLocation());
    return new Saml2X509Credential(
        certificate,
        Saml2X509Credential.Saml2X509CredentialType.ENCRYPTION,
        Saml2X509Credential.Saml2X509CredentialType.VERIFICATION);
  }

  private RSAPrivateKey readPrivateKey(Resource location) {
    Assert.state(location != null, "No private key location specified");
    Assert.state(location.exists(), () -> "Private key location '" + location + "' does not exist");
    try (InputStream inputStream = location.getInputStream()) {
      PemContent pemContent = PemContent.load(inputStream);
      PrivateKey privateKey = pemContent.getPrivateKey();
      Assert.state(
          privateKey instanceof RSAPrivateKey,
          () -> "PrivateKey in resource '" + location + "' must be an RSAPrivateKey");
      return (RSAPrivateKey) privateKey;
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  private X509Certificate readCertificate(Resource location) {
    Assert.state(location != null, "No certificate location specified");
    Assert.state(location.exists(), () -> "Certificate location '" + location + "' does not exist");
    try (InputStream inputStream = location.getInputStream()) {
      PemContent pemContent = PemContent.load(inputStream);
      List<X509Certificate> certificates = pemContent.getCertificates();
      return certificates.get(0);
    } catch (Exception ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  /**
   * Matches when at least one {@code spring.security.saml2.relyingparty.registration.*} entry is
   * configured (mirrors Boot 3's {@code RegistrationConfiguredCondition}).
   */
  static class RegistrationConfiguredCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
      // NB: Environment.getProperty(prefix, Map.class) does NOT collect sub-properties;
      // bind explicitly like Boot 3's RegistrationConfiguredCondition did.
      return Binder.get(context.getEnvironment())
          .bind(
              "spring.security.saml2.relyingparty.registration",
              Bindable.mapOf(String.class, Object.class))
          .map(registrations -> !registrations.isEmpty())
          .orElse(false);
    }
  }
}
