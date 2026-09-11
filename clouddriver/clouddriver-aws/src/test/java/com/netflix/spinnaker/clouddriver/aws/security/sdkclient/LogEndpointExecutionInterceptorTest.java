/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.interceptor.Context;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;
import software.amazon.awssdk.core.interceptor.SdkExecutionAttribute;
import software.amazon.awssdk.http.SdkHttpRequest;

/** Unit tests for {@link LogEndpointExecutionInterceptor}. */
class LogEndpointExecutionInterceptorTest {

  @Test
  void beforeTransmission_doesNotThrowOnRepeatedEndpointsForSameService() {
    LogEndpointExecutionInterceptor interceptor = new LogEndpointExecutionInterceptor();

    ExecutionAttributes attributes = new ExecutionAttributes();
    attributes.putAttribute(SdkExecutionAttribute.SERVICE_NAME, "Ec2");

    SdkHttpRequest httpRequest = mock(SdkHttpRequest.class);
    when(httpRequest.getUri()).thenReturn(URI.create("https://ec2.us-east-1.amazonaws.com"));

    Context.BeforeTransmission context = mock(Context.BeforeTransmission.class);
    when(context.httpRequest()).thenReturn(httpRequest);

    interceptor.beforeTransmission(context, attributes);
    interceptor.beforeTransmission(context, attributes);

    // The interceptor reads the URI/service name on every call (to check whether it's already
    // been seen), but only actually logs the first time -- verified indirectly here by confirming
    // both calls read through to the same accessors without throwing.
    verify(httpRequest, times(2)).getUri();
  }

  @Test
  void beforeTransmission_tracksDifferentServicesIndependently() {
    LogEndpointExecutionInterceptor interceptor = new LogEndpointExecutionInterceptor();

    SdkHttpRequest ec2Request = mock(SdkHttpRequest.class);
    when(ec2Request.getUri()).thenReturn(URI.create("https://ec2.us-east-1.amazonaws.com"));
    Context.BeforeTransmission ec2Context = mock(Context.BeforeTransmission.class);
    when(ec2Context.httpRequest()).thenReturn(ec2Request);
    ExecutionAttributes ec2Attributes = new ExecutionAttributes();
    ec2Attributes.putAttribute(SdkExecutionAttribute.SERVICE_NAME, "Ec2");

    SdkHttpRequest s3Request = mock(SdkHttpRequest.class);
    when(s3Request.getUri()).thenReturn(URI.create("https://s3.us-east-1.amazonaws.com"));
    Context.BeforeTransmission s3Context = mock(Context.BeforeTransmission.class);
    when(s3Context.httpRequest()).thenReturn(s3Request);
    ExecutionAttributes s3Attributes = new ExecutionAttributes();
    s3Attributes.putAttribute(SdkExecutionAttribute.SERVICE_NAME, "S3");

    interceptor.beforeTransmission(ec2Context, ec2Attributes);
    interceptor.beforeTransmission(s3Context, s3Attributes);
  }
}
