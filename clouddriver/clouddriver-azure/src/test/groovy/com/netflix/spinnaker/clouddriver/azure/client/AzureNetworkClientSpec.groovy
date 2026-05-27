/*
 * Copyright 2024 The original authors.
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

import com.azure.core.http.HttpResponse
import com.azure.core.management.exception.ManagementException
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.invocation.InvocationOnMock
import spock.lang.Specification

/**
 * Tests for AzureNetworkClient.getAppGateway exception-handling logic.
 *
 * AzureNetworkClient requires real Azure credentials at construction time, so we
 * allocate an instance via Unsafe (bypassing the constructor) and then use
 * Mockito.mockStatic to intercept the static AzureBaseClient.executeOp method.
 */
class AzureNetworkClientSpec extends Specification {

  static final String RESOURCE_GROUP = "my-rg"
  static final String AGW_NAME = "myapp-main-v001"

  /**
   * Create a ManagementException that fakes a given HTTP status code.
   */
  private static ManagementException managementExceptionWithStatus(int statusCode) {
    def httpResponse = Mockito.mock(HttpResponse)
    Mockito.when(httpResponse.getStatusCode()).thenReturn(statusCode)
    new ManagementException("Simulated ${statusCode}", httpResponse)
  }

  /**
   * Allocate an AzureNetworkClient without running AzureBaseClient's constructor
   * (which would try to contact Azure). The azure field is left null but that is
   * fine because executeOp will be mocked out before getAppGateway calls it.
   */
  private static AzureNetworkClient allocateClient() {
    def f = sun.misc.Unsafe.getDeclaredField("theUnsafe")
    f.accessible = true
    def unsafe = f.get(null) as sun.misc.Unsafe
    unsafe.allocateInstance(AzureNetworkClient) as AzureNetworkClient
  }

  def 'getAppGateway returns null for a 404 ManagementException'() {
    given:
    def client = allocateClient()
    def notFoundEx = managementExceptionWithStatus(HttpURLConnection.HTTP_NOT_FOUND)

    MockedStatic<AzureBaseClient> staticMock = Mockito.mockStatic(AzureBaseClient)
    staticMock.when({
      AzureBaseClient.executeOp(Mockito.any(Closure), Mockito.anyLong())
    }).thenThrow(notFoundEx)
    // also stub the single-arg overload
    staticMock.when({
      AzureBaseClient.executeOp(Mockito.any(Closure))
    }).thenThrow(notFoundEx)
    staticMock.when({
      AzureBaseClient.resourceNotFound(notFoundEx)
    }).thenReturn(true)

    when:
    def result = client.getAppGateway(RESOURCE_GROUP, AGW_NAME)

    then: '404 → null returned, no exception propagated'
    result == null

    cleanup:
    staticMock.close()
  }

  def 'getAppGateway rethrows a non-404 exception with resource group and AGW name in the message'() {
    given:
    def client = allocateClient()
    def cause = new RuntimeException("simulated NPE in description builder")

    MockedStatic<AzureBaseClient> staticMock = Mockito.mockStatic(AzureBaseClient)
    staticMock.when({
      AzureBaseClient.executeOp(Mockito.any(Closure), Mockito.anyLong())
    }).thenThrow(cause)
    staticMock.when({
      AzureBaseClient.executeOp(Mockito.any(Closure))
    }).thenThrow(cause)
    staticMock.when({
      AzureBaseClient.resourceNotFound(cause)
    }).thenReturn(false)

    when:
    client.getAppGateway(RESOURCE_GROUP, AGW_NAME)

    then: 'a RuntimeException wrapping the original cause is rethrown'
    def ex = thrown(RuntimeException)
    ex.cause.is(cause)
    ex.message.contains(RESOURCE_GROUP)
    ex.message.contains(AGW_NAME)

    cleanup:
    staticMock.close()
  }
}
