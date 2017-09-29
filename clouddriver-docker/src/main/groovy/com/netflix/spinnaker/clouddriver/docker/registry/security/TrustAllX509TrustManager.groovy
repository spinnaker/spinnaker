package com.netflix.spinnaker.clouddriver.docker.registry.security

import java.security.cert.CertificateException
import java.security.cert.X509Certificate

import javax.net.ssl.X509TrustManager

class TrustAllX509TrustManager implements X509TrustManager {

  @Override
  void checkServerTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
  }

  @Override
  void checkClientTrusted(final X509Certificate[] chain, final String authType) throws CertificateException {
  }

  @Override
  X509Certificate[] getAcceptedIssuers() {
    X509Certificate[] result = new X509Certificate[0];
    return result;
  }

}
