/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.amos.gce

import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.http.HttpTransport
import com.google.api.client.json.JsonFactory
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.SecurityUtils
import com.google.api.services.compute.Compute
import com.google.api.services.compute.ComputeScopes
import com.google.api.services.compute.model.Region
import com.google.api.services.compute.model.RegionList
import com.netflix.spinnaker.amos.AccountCredentials
import org.apache.commons.codec.binary.Base64
import org.springframework.web.client.RestTemplate

import java.security.PrivateKey

class GoogleNamedAccountCredentials implements AccountCredentials<GoogleCredentials> {
  private static final String APPLICATION_NAME = "Spinnaker"
  private static final String PROVIDER = "gce";

  private final String kmsServer
  private final String pkcs12Password
  private final Map<String, List<String>> regionToZonesMap

  final String accountName
  final String projectName
  final GoogleCredentials credentials

  GoogleNamedAccountCredentials(String kmsServer, String pkcs12Password, String accountName, String projectName) {
    this.kmsServer = kmsServer
    this.pkcs12Password = pkcs12Password
    this.accountName = accountName
    this.projectName = projectName
    this.credentials = buildCredentials()
    this.regionToZonesMap = queryRegions(credentials.compute, projectName)
  }

  String getName() {
    return accountName
  }

  Map<String, List<String>> getRegions() {
    return regionToZonesMap
  }

  GoogleCredentials getCredentials() {
    return credentials
  }

  private GoogleCredentials buildCredentials() {
    JsonFactory JSON_FACTORY = JacksonFactory.defaultInstance
    HttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport()
    def rt = new RestTemplate()
    def map = rt.getForObject("${kmsServer}/credentials/${accountName}", Map)
    def key = new ByteArrayInputStream(Base64.decodeBase64(map.key as String))
    PrivateKey privateKey = SecurityUtils.loadPrivateKeyFromKeyStore(SecurityUtils.pkcs12KeyStore,
                                                                     key,
                                                                     pkcs12Password,
                                                                     "privatekey",
                                                                     pkcs12Password)
    def credential = new GoogleCredential.Builder().setTransport(httpTransport)
            .setJsonFactory(JSON_FACTORY)
            .setServiceAccountId(map.email as String)
            .setServiceAccountScopes(Collections.singleton(ComputeScopes.COMPUTE))
            .setServiceAccountPrivateKey(privateKey).build()
    def compute = new Compute.Builder(httpTransport,
                                      JSON_FACTORY,
                                      null)
            .setApplicationName(APPLICATION_NAME)
            .setHttpRequestInitializer(credential)
            .build()
    return new GoogleCredentials(projectName,
                                 compute,
                                 httpTransport,
                                 JSON_FACTORY,
                                 map.email as String,
                                 privateKey)
  }

  private static Map<String, List<String>> queryRegions(Compute compute, String projectName) {
    Map<String, List<String>> regionToZonesMap = new HashMap<String, List<String>>()
    RegionList regionList = compute.regions().list(projectName).execute()

    regionList.getItems().each { Region region ->
      regionToZonesMap[region.getName()] = region.getZones().collect { zoneUrl ->
        getLocalName(zoneUrl)
      }
    }

    return regionToZonesMap
  }

  private static String getLocalName(String fullUrl) {
    def urlParts = fullUrl.split("/")

    return urlParts[urlParts.length - 1]
  }

  @Override
  public String getProvider() {
    return PROVIDER;
  }
}
