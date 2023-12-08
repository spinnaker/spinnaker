/*
 * Copyright 2023 Armory, Inc.
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

package com.netflix.spinnaker.clouddriver.lambda.deploy.ops;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.amazonaws.ClientConfiguration;
import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionOutputDescription;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AbstractLambdaAtomicOperationTest {

  @Test
  public void verifyLambdaClientGetsDefaultConfigPassed() {
    InvokeLambdaFunctionDescription desc = new InvokeLambdaFunctionDescription();
    desc.setRegion("someplace");
    NetflixAmazonCredentials creds = mock(NetflixAmazonCredentials.class);
    when(creds.getLambdaEnabled()).thenReturn(true);
    desc.setCredentials(creds);
    AbstractLambdaAtomicOperation<
            InvokeLambdaFunctionDescription, InvokeLambdaFunctionOutputDescription>
        operation =
            new AbstractLambdaAtomicOperation<>(desc, null) {

              @Override
              public InvokeLambdaFunctionOutputDescription operate(
                  List<InvokeLambdaFunctionOutputDescription> priorOutputs) {
                return null;
              }
            };
    operation.operationsConfig = new LambdaServiceConfig();
    operation.amazonClientProvider = mock(AmazonClientProvider.class);
    ArgumentCaptor<ClientConfiguration> captureclientConfig =
        ArgumentCaptor.forClass(ClientConfiguration.class);
    operation.getLambdaClient();
    verify(operation.amazonClientProvider)
        .getAmazonLambda(any(), captureclientConfig.capture(), eq("someplace"));
    assertEquals(3, captureclientConfig.getValue().getMaxErrorRetry());
    assertEquals(50000, captureclientConfig.getValue().getSocketTimeout());
  }

  @Test
  public void verifyLambdaClientSetsTimeouts() {
    InvokeLambdaFunctionDescription desc = new InvokeLambdaFunctionDescription();
    desc.setRegion("someplace");
    NetflixAmazonCredentials creds = mock(NetflixAmazonCredentials.class);
    when(creds.getLambdaEnabled()).thenReturn(true);
    desc.setCredentials(creds);
    AbstractLambdaAtomicOperation<
            InvokeLambdaFunctionDescription, InvokeLambdaFunctionOutputDescription>
        operation =
            new AbstractLambdaAtomicOperation<>(desc, null) {

              @Override
              public InvokeLambdaFunctionOutputDescription operate(
                  List<InvokeLambdaFunctionOutputDescription> priorOutputs) {
                return null;
              }
            };
    operation.operationsConfig = new LambdaServiceConfig();
    operation.operationsConfig.setInvokeTimeoutMs(300 * 1000);
    operation.operationsConfig.getRetry().setRetries(0);
    operation.amazonClientProvider = mock(AmazonClientProvider.class);
    ArgumentCaptor<ClientConfiguration> captureclientConfig =
        ArgumentCaptor.forClass(ClientConfiguration.class);
    operation.getLambdaClient();
    verify(operation.amazonClientProvider)
        .getAmazonLambda(any(), captureclientConfig.capture(), eq("someplace"));
    //    assertEquals(4, captureclientConfig.getValue().getMaxErrorRetry());
    assertEquals(0, captureclientConfig.getValue().getMaxErrorRetry());
    assertEquals(300000, captureclientConfig.getValue().getSocketTimeout());
  }
}
