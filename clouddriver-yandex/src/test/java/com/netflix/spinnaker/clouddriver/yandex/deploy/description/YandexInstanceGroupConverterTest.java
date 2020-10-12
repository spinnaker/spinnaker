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

package com.netflix.spinnaker.clouddriver.yandex.deploy.description;

import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spinnaker.clouddriver.yandex.deploy.AbstractYandexDeployTest;
import com.netflix.spinnaker.clouddriver.yandex.model.YandexCloudServerGroup;
import com.netflix.spinnaker.clouddriver.yandex.provider.agent.YandexServerGroupCachingAgent;
import com.netflix.spinnaker.clouddriver.yandex.service.converter.YandexInstanceGroupConverter;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import yandex.cloud.api.compute.v1.instancegroup.InstanceGroupServiceOuterClass;

class YandexInstanceGroupConverterTest extends AbstractYandexDeployTest {
  private YandexCloudServerGroup.InstanceTemplate convert(YandexInstanceGroupDescription ig) {
    InstanceGroupServiceOuterClass.CreateInstanceGroupRequest request =
        YandexInstanceGroupConverter.mapToCreateRequest(ig);
    return YandexServerGroupCachingAgent.convertInstanceTemplate(request.getInstanceTemplate());
  }

  @Test
  void mapToCreateRequest() throws IOException {
    YandexInstanceGroupDescription ig =
        getObject("/operations/create_server_group.json", YandexInstanceGroupDescription.class);
    YandexCloudServerGroup.InstanceTemplate templateNoNulls =
        convert(ig); // first time null replaced with empty
    ig.setInstanceTemplate(templateNoNulls);
    assertEquals(templateNoNulls, convert(ig));
  }
}
