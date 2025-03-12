package com.netflix.spinnaker.gate.security.x509;

import java.security.cert.X509Certificate;

public interface X509UserIdentifierExtractor {

  /**
   * Extracts the user identifier (email, application name, instance id, etc) from the X509
   * certificate.
   */
  String fromCertificate(X509Certificate cert);
}
