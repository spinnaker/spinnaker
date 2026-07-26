# SAML Configuration Migration Guide

Spinnaker previously used custom `saml.*` properties for all SAML settings. Connection-level
settings (IdP metadata, entity ID, credentials, ACS location) are now configured via Spring
Boot's native `spring.security.saml2.relyingparty.registration.*` properties. Spinnaker-specific
settings (user attribute mapping, required roles, role processing) remain under `saml.*`.

---

## What Changed

### Removed `saml.*` properties and their replacements

| Removed property | Replacement |
|---|---|
| `saml.metadata-url` | `spring.security.saml2.relyingparty.registration.<id>.assertingparty.metadata-uri` |
| `saml.issuer-id` | `spring.security.saml2.relyingparty.registration.<id>.entity-id` |
| `saml.registration-id` | The `<id>` key in the registration map |
| `saml.sign-requests` | `spring.security.saml2.relyingparty.registration.<id>.assertingparty.singlesignon.sign-request` |
| `saml.signing-credentials[*].privateKeyLocation` | `spring.security.saml2.relyingparty.registration.<id>.signing.credentials[*].private-key-location` |
| `saml.signing-credentials[*].certificateLocation` | `spring.security.saml2.relyingparty.registration.<id>.signing.credentials[*].certificate-location` |
| `saml.key-store` | `spring.security.saml2.relyingparty.registration.<id>.decryption.credentials[*].private-key-location` (PEM — see below) |
| `saml.key-store-password` | _(no equivalent — use PEM-based decryption credentials)_ |
| `saml.key-store-alias-name` | _(no equivalent — use PEM-based decryption credentials)_ |

### Unchanged `saml.*` properties

- `saml.enabled`
- `saml.login-processing-url`
- `saml.required-roles`
- `saml.sort-roles`
- `saml.force-lowercase-roles`
- `saml.user-attribute-mapping.*`

---

## Minimal Migration Example

### Before

```yaml
saml:
  enabled: true
  issuer-id: spinnaker
  metadata-url: https://idp.example.com/saml/metadata
  sign-requests: true
  signing-credentials:
    - private-key-location: /etc/gate/saml/private_key.pem
      certificate-location: /etc/gate/saml/certificate.pem
  user-attribute-mapping:
    email: email
    roles: memberOf
```

### After

```yaml
saml:
  enabled: true
  user-attribute-mapping:
    email: email
    roles: memberOf

spring:
  security:
    saml2:
      relyingparty:
        registration:
          SSO:                          # registration ID (arbitrary key, used in URLs)
            entity-id: spinnaker
            assertingparty:
              metadata-uri: https://idp.example.com/saml/metadata
              singlesignon:
                sign-request: true
            signing:
              credentials:
                - private-key-location: /etc/gate/saml/private_key.pem
                  certificate-location: /etc/gate/saml/certificate.pem
```

---

## ACS URL / Redirect URI

Spring Boot's default assertion consumer service (ACS) URL path is:

```
/login/saml2/sso/{registrationId}    # e.g. /login/saml2/sso/SSO
```

The previous Spinnaker default was `/saml/{registrationId}`. **Long term, it's
recommended that you update your IdP redirect-URI configuration** to the new path.
For backwards compliance, spinnaker sets an override for this which enables the old path functionally.  YOU MUST
set both of these configuration properties to have this work.  To use the new default paths remove both of these
configuration properties.

```yaml
saml:
  login-processing-url: /saml/SSO
spring:
  security:
    saml2:
      relyingparty:
        registration:
          SSO:
            acs:
              location: "{baseUrl}/saml/SSO"   # keeps the legacy path
```

The best long term path is to update your IdP redirect URI to the Spring Boot default
(`/login/saml2/sso/SSO`) and remove these values.

---

## Keystore (JKS/PKCS12) to PEM Migration

The old `saml.key-store` properties loaded decryption credentials from a Java keystore. Spring
Boot's native configuration uses PEM files instead.

Extract PEM files from your existing keystore:

```bash
# Export the certificate
keytool -exportcert -alias mykey -keystore saml.p12 -storepass <password> \
  -rfc -file saml_decrypt_cert.pem

# Export the private key (requires OpenSSL)
openssl pkcs12 -in saml.p12 -passin pass:<password> -nocerts -nodes \
  | openssl pkcs8 -topk8 -nocrypt -out saml_decrypt_key.pem
```

Then configure decryption in Spring Boot:

```yaml
spring:
  security:
    saml2:
      relyingparty:
        registration:
          SSO:
            decryption:
              credentials:
                - private-key-location: /etc/gate/saml/saml_decrypt_key.pem
                  certificate-location: /etc/gate/saml/saml_decrypt_cert.pem
```

---

## Halyard Users

Halyard's `hal config security authn saml` commands still work and generate the `saml.*`
properties listed above. The Halyard-generated config is not automatically migrated; update
`gate-local.yml` (or equivalent) manually using the examples above if deploying without Halyard.
