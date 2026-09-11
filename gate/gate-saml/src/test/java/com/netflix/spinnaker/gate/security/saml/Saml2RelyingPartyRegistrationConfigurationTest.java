package com.netflix.spinnaker.gate.security.saml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;

class Saml2RelyingPartyRegistrationConfigurationTest {

  private Saml2RelyingPartyProperties properties() {
    Saml2RelyingPartyProperties properties = new Saml2RelyingPartyProperties();
    Saml2RelyingPartyProperties.Registration registration =
        new Saml2RelyingPartyProperties.Registration();
    registration.setEntityId("spinnaker-test");
    registration.getAcs().setLocation("{baseUrl}/saml/{registrationId}");
    registration.getAssertingparty().setEntityId("https://idp.example.com/metadata");
    registration.getAssertingparty().getSinglesignon().setUrl("https://idp.example.com/sso");
    registration.getAssertingparty().getSinglesignon().setSignRequest(true);
    Saml2RelyingPartyProperties.Registration.Signing.Credential credential =
        new Saml2RelyingPartyProperties.Registration.Signing.Credential();
    credential.setPrivateKeyLocation(new ClassPathResource("private_key.pem"));
    credential.setCertificateLocation(new ClassPathResource("certificate.pem"));
    registration.getSigning().setCredentials(java.util.List.of(credential));
    properties.getRegistration().put("SSO", registration);
    return properties;
  }

  @Test
  void buildsRepositoryFromProperties() {
    RelyingPartyRegistrationRepository repository =
        new Saml2RelyingPartyRegistrationConfiguration()
            .relyingPartyRegistrationRepository(properties());

    RelyingPartyRegistration registration = repository.findByRegistrationId("SSO");
    assertNotNull(registration, "expected SSO registration");
    assertEquals("spinnaker-test", registration.getEntityId());
    assertEquals(
        "{baseUrl}/saml/{registrationId}", registration.getAssertionConsumerServiceLocation());
    assertTrue(
        registration.getAssertingPartyMetadata().getWantAuthnRequestsSigned(),
        "expected signed authn requests");
    assertEquals(
        "https://idp.example.com/sso",
        registration.getAssertingPartyMetadata().getSingleSignOnServiceLocation());
    assertEquals(1, registration.getSigningX509Credentials().size(), "expected signing credential");
  }
}
