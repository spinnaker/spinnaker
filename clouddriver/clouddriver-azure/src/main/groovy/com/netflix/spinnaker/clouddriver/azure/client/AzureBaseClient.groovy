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

import com.azure.core.credential.TokenCredential
import com.azure.core.http.HttpClient
import com.azure.core.http.netty.NettyAsyncHttpClientBuilder
import com.azure.core.http.policy.ExponentialBackoff
import com.azure.core.http.policy.HttpLogDetailLevel
import com.azure.core.http.policy.RetryPolicy
import com.azure.core.http.rest.Response
import com.azure.core.management.AzureEnvironment
import com.azure.core.management.exception.ManagementException
import com.azure.core.management.profile.AzureProfile
import com.azure.identity.ClientSecretCredentialBuilder
import com.azure.resourcemanager.AzureResourceManager
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import java.time.Duration

@Slf4j
@CompileStatic
abstract class AzureBaseClient {
  final String subscriptionId
  final static long AZURE_ATOMICOPERATION_RETRY = 15
  final static int HTTP_TOO_MANY_REQUESTS = 429
  final static int DEFAULT_429_RETRY_INTERVAL_SEC = 30
  final static int MAX_429_RETRY_INTERVAL_SEC = 120
  static ObjectMapper mapper
  final AzureResourceManager azure

  /**
   * Constructor
   * @param subscriptionId - the Azure subscription to use
   */
  protected AzureBaseClient(String subscriptionId, AzureProfile azureProfile, TokenCredential credentials) {
    this.subscriptionId = subscriptionId
    mapper = new ObjectMapper().configure(SerializationFeature.INDENT_OUTPUT, true)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    this.azure = initialize(credentials, subscriptionId, azureProfile)
  }

  private AzureResourceManager initialize(TokenCredential credentials, String subscriptionId, AzureProfile azureProfile) {
    // Create custom HTTP client with increased header size limit to handle large Azure responses
    // Default Netty limit is 8192 bytes (8KB), increasing to 256KB to prevent TooLongHttpHeaderException
    HttpClient httpClient = new NettyAsyncHttpClientBuilder(
      reactor.netty.http.client.HttpClient.create()
        .httpResponseDecoder(httpResponseDecoderSpec -> 
          httpResponseDecoderSpec.maxHeaderSize(256 * 1024) // 256KB limit
        )
    ).build()

    def retryPolicy = new RetryPolicy(
      new ExponentialBackoff(8, Duration.ofSeconds(5), Duration.ofSeconds(120))
    )

    AzureResourceManager
      .configure()
      .withLogLevel(HttpLogDetailLevel.NONE)
      .withHttpClient(httpClient)
      .withRetryPolicy(retryPolicy)
      .authenticate(credentials, azureProfile)
      .withSubscription(subscriptionId)
  }

  /**
   * Create the application token credentials object to use for calls to Azure
   * @param clientId
   * @param tenantId
   * @param secret
   * @param configuredAzureEnvironment
   * @return
   */

  static TokenCredential getTokenCredentials(String clientId, String tenantId, String secret, String configuredAzureEnvironment) {

    def azureProfile = getAzureProfile(configuredAzureEnvironment)

    return new ClientSecretCredentialBuilder()
      .clientId(clientId)
      .clientSecret(secret)
      .tenantId(tenantId)
      .authorityHost(azureProfile.getEnvironment().getActiveDirectoryEndpoint())
      .build()

  }

  static AzureProfile getAzureProfile(String configuredAzureEnvironment) {
    switch (configuredAzureEnvironment) {
      case "AZURE_US_GOVERNMENT":
        return new AzureProfile(AzureEnvironment.AZURE_US_GOVERNMENT)
      case "AZURE_CHINA":
        return new AzureProfile(AzureEnvironment.AZURE_CHINA)
      case "AZURE_GERMANY":
        return new AzureProfile(AzureEnvironment.AZURE_GERMANY)
      default:
        return new AzureProfile(AzureEnvironment.AZURE)
    }
  }

  /**
   * Wrap the call to an Azure operation to handle retry attempts
   * @param operation - Operation to be execute
   * @param count - number of retry attempts
   * @return ServiceRespone returned from operation. If response results in 404 then return null
   */
  static <T> T executeOp(Closure<T> operation, long count = AZURE_ATOMICOPERATION_RETRY) {

    // Ensure that the operation will always at least try once
    long retryCount = count <= 0 ? count - 1 : 0
    long interval = 200

    while (retryCount < count) {
      try {
        retryCount++
        return operation()
      } catch (Exception e) {
        // if the resource wasn't found then there is no reason to keep trying
        if (resourceNotFound(e)) {
          log.warn("Azure resource(s) not found: $e.message")
          return null
        }
        // if there are still remaining attempts to be made, check to see if we should retry.
        else if (retryCount < count) {
          if (!handleTooManyRequestsResponse(e)) {
            if (canRetry(e)) {
              log.warn("Retrying Azure operation: $e.message")
              sleep(interval * retryCount)
            }
          }
        }
        else {
          throw e
        }
      }
    }
    null
  }

  /**
   * Determine if the operation can be retried based on the exception encountered
   * @param e - The exception encountered
   * @return True if it can be retried
   */
  private static boolean canRetry(Exception e) {
    boolean retry = false
    if (e instanceof ManagementException) {
      def code = (e as ManagementException).getResponse().getStatusCode()
      retry = (code == HttpURLConnection.HTTP_CLIENT_TIMEOUT
        || (code >= HttpURLConnection.HTTP_INTERNAL_ERROR && code <= HttpURLConnection.HTTP_GATEWAY_TIMEOUT))
    } else if (e instanceof SocketTimeoutException) {
      //If we get a socket time out try again
      retry = true
    }
    retry
  }

  /**
   * Handle when we get a "Too Many Requests" (429) response
   * Get the "Retry-After" value (in seconds) from the response headers and "sleep" before retrying
   * @param e - The exception encountered
   * @return True if the exception encountered was a 429 Response and it was handled
   */
  private static boolean handleTooManyRequestsResponse(Exception e) {
    if (e instanceof ManagementException) {
      def response = (e as ManagementException).getResponse()
      if (response.getStatusCode() == HTTP_TOO_MANY_REQUESTS) {
        int retryAfterIntervalSec = DEFAULT_429_RETRY_INTERVAL_SEC
        try {
          String retryAfterHeader = response.getHeaderValue("Retry-After")
          if (retryAfterHeader) {
            retryAfterIntervalSec = retryAfterHeader.toInteger()
          }
        } catch (Exception ignored) {
          // Retry-After may be an HTTP-date or unparseable; use default
        }
        if (retryAfterIntervalSec <= 0 || retryAfterIntervalSec > MAX_429_RETRY_INTERVAL_SEC) {
          retryAfterIntervalSec = DEFAULT_429_RETRY_INTERVAL_SEC
        }
        log.warn("Received 'Too Many Requests' (429) response from Azure. Retrying in $retryAfterIntervalSec seconds")
        sleep(retryAfterIntervalSec * 1000) // convert to milliseconds
        return true
      }
    }
    false
  }

  static Response<Void> deleteAzureResource(Closure azureOps, String resourceGroup, String resourceName, String parentResourceName, String msgRetry, String msgFail, long count = AZURE_ATOMICOPERATION_RETRY) {
    // The API call might return a timeout exception or some other Azure CloudException that is not the direct result of the operation
    //   we are trying to execute; retry and if the final retry fails then throw
    Response<Void> result = null
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
      catch (ManagementException e) {
        if (resourceNotFound(e)) {
          // resource was not found; must have been deleted already
          operationRetry = count
        } else {
          if (operationRetry >= count) {
            throw e
          }
          if (e.getResponse().getStatusCode() == HTTP_TOO_MANY_REQUESTS) {
            handleTooManyRequestsResponse(e)
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

  static Boolean resourceNotFound(Exception e) {
    // Azure SDK surfaces typed subclasses for some ARM error families; instanceof catches those, strict class equality wouldn't.
    e instanceof ManagementException && (e as ManagementException).getResponse().getStatusCode() == HttpURLConnection.HTTP_NOT_FOUND
  }

  /***
   * register the resource provider in the subscription
   * @param resourceManagerClient - an instance of the AzureResourceManagerClient
   */
  void register(AzureResourceManagerClient resourceManagerClient) {
    if (resourceManagerClient && providerNamespace) {
      resourceManagerClient.registerProvider(providerNamespace)
    }
  }

  /***
   * The namespace for the Azure Resource Provider
   * @return namespace of the resource provider
   */
  protected abstract String getProviderNamespace()
}
