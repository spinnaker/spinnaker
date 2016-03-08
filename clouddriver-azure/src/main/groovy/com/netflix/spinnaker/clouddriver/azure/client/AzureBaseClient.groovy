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
import com.fasterxml.jackson.databind.SerializationFeature
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.credentials.AzureEnvironment
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j


@Slf4j
@CompileStatic
public abstract class AzureBaseClient {
  final String subscriptionId

  static ObjectMapper mapper

  /**
   * Constructor
   * @param subscriptionId - the Azure subscription to use
   */
  protected AzureBaseClient(String subscriptionId) {
    this.subscriptionId = subscriptionId
    mapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }

  /**
   * Create the application token credentials object to use for calls to Azure
   * @param clientId
   * @param tenantId
   * @param secret
   * @return
   */
  static ApplicationTokenCredentials getTokenCredentials(String clientId, String tenantId, String secret) {
    new ApplicationTokenCredentials(clientId, tenantId, secret, AzureEnvironment.AZURE)
  }

}
