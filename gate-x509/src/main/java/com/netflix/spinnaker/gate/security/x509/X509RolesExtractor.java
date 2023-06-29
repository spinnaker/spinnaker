package com.netflix.spinnaker.gate.security.x509;

import java.security.cert.X509Certificate;
import java.util.Collection;

public interface X509RolesExtractor {

  /**
   * Loads the roles assigned to the {@link com.netflix.spinnaker.security.User User}, extracted
   * from the X509 certificate.
   *
   * @param cert
   * @return Roles assigned to the {@link com.netflix.spinnaker.security.User User}
   */
  Collection<String> fromCertificate(X509Certificate cert);
}
