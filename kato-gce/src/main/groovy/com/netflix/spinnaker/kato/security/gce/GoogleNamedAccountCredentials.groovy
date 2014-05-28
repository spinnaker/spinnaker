package com.netflix.spinnaker.kato.security.gce

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.SecurityUtils
import com.google.api.services.compute.Compute
import com.google.api.services.compute.ComputeScopes
import com.netflix.spinnaker.kato.security.NamedAccountCredentials
import org.apache.commons.codec.binary.Base64
import org.springframework.web.client.RestTemplate

import java.security.PrivateKey

class GoogleNamedAccountCredentials implements NamedAccountCredentials {
  final GoogleCredentials credentials

  GoogleNamedAccountCredentials(String projectName) {
    this.credentials = new GoogleCredentials(projectName, getCompute(projectName))
  }

  private static Compute getCompute(String projectName) {
    JsonFactory JSON_FACTORY = JacksonFactory.defaultInstance
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    def rt = new RestTemplate()
    def map = rt.getForObject("http://localhost:7909/credentials/${projectName}", Map)
    def key = new ByteArrayInputStream(Base64.decodeBase64(map.key as String))
    PrivateKey privateKey = SecurityUtils.loadPrivateKeyFromKeyStore(SecurityUtils.pkcs12KeyStore, key, "notasecret", "privatekey", "notasecret")
    def credential = new GoogleCredential.Builder().setTransport(httpTransport)
      .setJsonFactory(JSON_FACTORY)
      .setServiceAccountId(map.email as String)
      .setServiceAccountScopes(Collections.singleton(ComputeScopes.COMPUTE))
      .setServiceAccountPrivateKey(privateKey)
      .build()
    new Compute.Builder(
      httpTransport, JSON_FACTORY, null).setApplicationName("asgard")
      .setHttpRequestInitializer(credential).build()
  }

}
