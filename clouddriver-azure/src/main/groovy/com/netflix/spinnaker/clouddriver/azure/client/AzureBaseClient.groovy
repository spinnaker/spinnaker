/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.microsoft.windowsazure.Configuration
import com.microsoft.windowsazure.exception.ServiceException
import com.microsoft.windowsazure.management.configuration.ManagementConfiguration
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.apache.commons.io.IOUtils
import org.apache.http.HttpHost
import org.apache.http.HttpResponse
import org.apache.http.HttpStatus
import org.apache.http.client.methods.HttpGet
import org.apache.http.impl.client.HttpClientBuilder

@Slf4j
@CompileStatic
public abstract class AzureBaseClient {
  final String subscriptionId

  static ObjectMapper mapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

  protected AzureBaseClient(String subscriptionId) {
    this.subscriptionId = subscriptionId
  }

  protected Configuration buildConfiguration(AzureCredentials creds) throws Exception {
    try {
      String baseUri = creds.MANAGEMENT_URL
      return ManagementConfiguration.configure(
        null,
        new Configuration(),
        new URI(baseUri),
        this.subscriptionId,
        creds.getAccessToken())
    } catch (Exception e) {
      throw new RuntimeException("Failed to create Azure Resource Management Service", e)
    }
  }

  public <T> T getAzureRESTJson(AzureCredentials creds, String baseUrl, String targetUrl, List<String> queryParameters, Class<T> valueType){
    String url = AzureUtilities.getAzureRESTUrl(subscriptionId, baseUrl, targetUrl, queryParameters)

    try {
      // create HTTP Client
      def config = buildConfiguration(creds)
      HttpClientBuilder httpClientBuilder = config.create(HttpClientBuilder.class)

      String proxyHost = System.getProperty("http.proxyHost")
      String proxyPort = System.getProperty("http.proxyPort")
      if ((proxyHost != null) && (proxyPort != null)) {
        HttpHost proxy = new HttpHost(proxyHost, Integer.parseInt(proxyPort))
        if (proxy != null) {
          httpClientBuilder.setProxy(proxy)
        }
      }

      def httpClient = httpClientBuilder.build()

      // Create HTTP transport objects
      HttpGet httpRequest = new HttpGet(url)

      // Set Headers
      httpRequest.setHeader("Content-Type", "application/json")

      // Send Request
      HttpResponse httpResponse = httpClient.execute(httpRequest)
      int statusCode = httpResponse.getStatusLine().getStatusCode()
      if (statusCode != HttpStatus.SC_OK) {
        ServiceException ex = ServiceException.createFromJson(httpRequest, null, httpResponse, httpResponse.getEntity())
        throw ex
      }

      // Deserialize Response
      def entity = httpResponse.getEntity()
      def responseContent = entity.getContent()
      String resultJson = IOUtils.toString(responseContent)

      if (resultJson) {
        return mapper.readValue(resultJson, valueType)
      }
    }
    catch (Exception e) {
      log.info("getVMImagesAll -> Unexpected exception " + e.toString())
      throw new RuntimeException("Unable to make Azure REST call to ${url}", e)
    }

    null
  }
}
