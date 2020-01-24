/*
 * Copyright 2019 Huawei Technologies Co.,Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.huaweicloud;

import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class HuaweiCloudServerGroupCreator implements ServerGroupCreator {

  @Override
  public boolean isKatoResultExpected() {
    return false;
  }

  @Override
  public String getCloudProvider() {
    return "huaweicloud";
  }

  @Override
  public Optional<String> getHealthProviderName() {
    return Optional.of("HuaweiCloud");
  }

  @Override
  public List<Map> getOperations(Stage stage) {
    Map<String, Object> operation = new HashMap();

    if (stage.getContext().containsKey("cluster")) {
      operation.putAll((Map) stage.getContext().get("cluster"));
    } else {
      operation.putAll(stage.getContext());
    }

    return new ArrayList() {
      {
        add(
            new HashMap() {
              {
                put(OPERATION, operation);
              }
            });
      }
    };
  }
}
