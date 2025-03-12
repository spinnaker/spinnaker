/*
 * Copyright 2020 YANDEX LLC
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

package com.netflix.spinnaker.clouddriver.yandex.deploy.converter;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spinnaker.clouddriver.deploy.DeployAtomicOperation;
import com.netflix.spinnaker.clouddriver.yandex.deploy.AbstractYandexDeployTest;
import com.netflix.spinnaker.clouddriver.yandex.deploy.description.YandexInstanceGroupDescription;
import java.io.IOException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CreateYandexServerGroupAtomicOperationConverterTest extends AbstractYandexDeployTest {
  private final OperationConverter<YandexInstanceGroupDescription, DeployAtomicOperation> converter;

  CreateYandexServerGroupAtomicOperationConverterTest() {
    converter = new YandexOperationConvertersFactory.CreateServerGroup();
    converter.setAccountCredentialsProvider(accountCredentialsProvider);
    converter.setObjectMapper(objectMapper);
  }

  @Test
  void convertDescription() throws IOException {
    Map<String, Object> description = getDescription("/operations/create_server_group.json");
    YandexInstanceGroupDescription result = converter.convertDescription(description);

    assertThat(result.getApplication()).isNotNull();
    assertThat(result.getEnableTraffic()).isEqualTo(false);
    assertThat(result.getInstanceTemplate().getResourcesSpec().getMemory()).isEqualTo(2);
  }
}
