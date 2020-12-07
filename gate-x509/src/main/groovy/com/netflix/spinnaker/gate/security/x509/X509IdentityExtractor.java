package com.netflix.spinnaker.gate.security.x509;

import com.netflix.spinnaker.gate.security.RequestIdentityExtractor;
import java.security.cert.X509Certificate;
import java.util.Objects;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;

@Slf4j
public class X509IdentityExtractor implements RequestIdentityExtractor {

  private static final String REQUEST_CERT_ATTRIBUTE = "javax.servlet.request.X509Certificate";
  private final X509AuthenticationUserDetailsService userDetailsService;

  public X509IdentityExtractor(X509AuthenticationUserDetailsService userDetailsService) {
    this.userDetailsService = Objects.requireNonNull(userDetailsService);
  }

  private String identityFromCertificate(Object x509CertAttribute) {

    if (x509CertAttribute == null) {
      return null;
    }

    if (!(x509CertAttribute instanceof X509Certificate[])) {
      log.warn(
          "HttpServletRequest attribute {} did not match expected type {} (was {})",
          REQUEST_CERT_ATTRIBUTE,
          X509Certificate[].class,
          x509CertAttribute.getClass());
      return null;
    }

    X509Certificate[] x509Certificates = (X509Certificate[]) x509CertAttribute;
    if (x509Certificates.length == 0) {
      return null;
    }

    return userDetailsService.identityFromCertificate(x509Certificates[0]);
  }

  @Override
  public boolean supports(HttpServletRequest httpServletRequest) {
    return httpServletRequest.getAttribute(REQUEST_CERT_ATTRIBUTE) != null
        && SecurityContextHolder.getContext().getAuthentication() == null;
  }

  @Override
  public String extractIdentity(HttpServletRequest httpServletRequest) {
    return identityFromCertificate(httpServletRequest.getAttribute(REQUEST_CERT_ATTRIBUTE));
  }
}
