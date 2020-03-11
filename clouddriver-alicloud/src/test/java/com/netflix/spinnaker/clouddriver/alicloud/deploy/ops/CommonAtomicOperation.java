/*
 * Copyright 2019 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.alicloud.deploy.ops;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aliyuncs.IAcsClient;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.netflix.spinnaker.clouddriver.alicloud.common.ClientFactory;
import com.netflix.spinnaker.clouddriver.alicloud.security.AliCloudCredentials;
import com.netflix.spinnaker.clouddriver.model.ClusterProvider;
import java.util.List;
import spock.lang.Subject;

public class CommonAtomicOperation {

  public static final String ACCOUNT = "test-account";
  public static final String REGION = "cn-test";

  public final List priorOutputs = mock(List.class);

  public static AliCloudCredentials credentials = mock(AliCloudCredentials.class);
  public static IAcsClient client = mock(IAcsClient.class);
  public static ClientFactory clientFactory = mock(ClientFactory.class);
  static List<ClusterProvider> clusterProviders = mock(List.class);

  static {
    when(credentials.getName()).thenReturn(ACCOUNT);
    when(credentials.getAccessKeyId()).thenReturn("test-ak");
    when(credentials.getAccessSecretKey()).thenReturn("test-sk");
    when(clientFactory.createClient(anyString(), anyString(), anyString())).thenReturn(client);
  }

  @Subject
  public ObjectMapper objectMapper =
      new ObjectMapper()
          .configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false)
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
}
