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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.clouddriver.aws.security.AmazonClientProvider;
import com.netflix.spinnaker.clouddriver.aws.security.NetflixAmazonCredentials;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionDescription;
import com.netflix.spinnaker.clouddriver.lambda.deploy.description.InvokeLambdaFunctionOutputDescription;
import com.netflix.spinnaker.config.LambdaServiceConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import software.amazon.awssdk.services.lambda.LambdaClient;

class AbstractLambdaAtomicOperationTest {

  @Test
  public void verifyLambdaClientUsesV2Provider() {
    InvokeLambdaFunctionDescription desc = new InvokeLambdaFunctionDescription();
    desc.setRegion("someplace");
    NetflixAmazonCredentials creds = mock(NetflixAmazonCredentials.class);
    when(creds.isLambdaEnabled()).thenReturn(true);
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
    operation.amazonClientProvider = mock(AmazonClientProvider.class);
    operation.operationsConfig = new LambdaServiceConfig();
    when(operation.amazonClientProvider.getLambdaV2(eq(creds), eq("someplace"), any()))
        .thenReturn(mock(LambdaClient.class));

    LambdaClient client = operation.getLambdaClient();
    assertNotNull(client);
    verify(operation.amazonClientProvider).getLambdaV2(eq(creds), eq("someplace"), any());
  }

  @Test
  public void verifyLambdaClientDisabledAccountThrows() {
    InvokeLambdaFunctionDescription desc = new InvokeLambdaFunctionDescription();
    desc.setRegion("someplace");
    NetflixAmazonCredentials creds = mock(NetflixAmazonCredentials.class);
    when(creds.isLambdaEnabled()).thenReturn(false);
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

    assertThrows(RuntimeException.class, operation::getLambdaClient);
  }

  @Test
  void tcpKeepAliveShouldBindFromProperties() {
    // Given
    Map<String, String> props = new HashMap<>();
    props.put("aws.lambda.tcpKeepAlive", "true");
    // When
    LambdaServiceConfig config =
        new Binder(new MapConfigurationPropertySource(props))
            .bind("aws.lambda", LambdaServiceConfig.class)
            .get();

    // Then
    assertThat(config.isTcpKeepAlive()).isTrue();
  }
}
