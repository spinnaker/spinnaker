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
import com.microsoft.azure.CloudException
import com.microsoft.azure.credentials.ApplicationTokenCredentials
import com.microsoft.azure.credentials.AzureEnvironment
import com.microsoft.rest.ServiceResponse
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

@Slf4j
@CompileStatic
public abstract class AzureBaseClient {
  final String subscriptionId
  final static long AZURE_ATOMICOPERATION_RETRY = 5
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

  static Object getAzureOps(Closure getOps, String msgRetry, String msgFail, long count = AZURE_ATOMICOPERATION_RETRY) {
    // The API call might return a timeout exception or some other Azure CloudException that is not the direct result of the operation
    //   we are trying to execute; retry and if the final retry fails then throw
    Object result = null
    long operationRetry = 0
    while (operationRetry < count) {
      try {
        operationRetry ++
        result = getOps()
        operationRetry = count
      }
      catch (Exception e) {
        sleep(200)
        log.warn("${msgRetry}: ${e.message}")
        if (operationRetry >= count) {
          log.error(msgFail)
          throw e
        }
      }
    }

    result
  }

  static ServiceResponse<Void> deleteAzureResource( Closure azureOps, String resourceGroup, String resourceName, String parentResourceName, String msgRetry, String msgFail, long count = AZURE_ATOMICOPERATION_RETRY) {
    // The API call might return a timeout exception or some other Azure CloudException that is not the direct result of the operation
    //   we are trying to execute; retry and if the final retry fails then throw
    ServiceResponse<Void> result = null
    long operationRetry = 0
    while (operationRetry < count) {
      try {
        operationRetry ++
        if (parentResourceName) {
          azureOps(resourceGroup, parentResourceName, resourceName)
        } else {
          azureOps(resourceGroup, resourceName)
        }
        operationRetry = count
      }
      catch (CloudException e) {
        if (e.body.code == "404") {
          // resource was not found; must have been deleted already
          operationRetry = count
        } else {
          if (operationRetry >= count) {
            throw e
          }
        }
      }
      catch (Exception e) {
        sleep(200)
        log.warn("${msgRetry}: ${e.message}")
        if (operationRetry >= count) {
          // Add something to the log to show that the resource deletion failed then rethrow the exception
          log.error(msgFail)
          throw e
        }
      }
    }

    result
  }

  static Boolean resourceNotFound(int responseStatusCode) {
    responseStatusCode == HttpURLConnection.HTTP_NO_CONTENT || responseStatusCode == HttpURLConnection.HTTP_NOT_FOUND
  }

  /***
   * register the resource provider in the subscription
   * @param resourceManagerClient - an instance of the AzureResourceManagerClient
   */
  void register(AzureResourceManagerClient resourceManagerClient) {
    if (resourceManagerClient && !"".equals(providerNamespace)) {
      resourceManagerClient.registerProvider(providerNamespace)
    }
  }

  /***
   * The namespace for the Azure Resource Provider
   * @return namespace of the resource provider
   */
  protected abstract String getProviderNamespace()

}
