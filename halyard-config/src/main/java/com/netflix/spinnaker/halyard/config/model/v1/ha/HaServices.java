/*
 * Copyright 2018 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 *
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.ha;

import com.netflix.spinnaker.halyard.config.model.v1.node.Node;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = false)
public class HaServices extends Node implements Cloneable {
  private ClouddriverHaService clouddriver = new ClouddriverHaService();
  private EchoHaService echo = new EchoHaService();

  @Override
  public String getNodeName() {
    return "haServices";
  }

  public static Class<? extends HaService> translateHaServiceType(String serviceName) {
    Optional<? extends Class<?>> res =
        Arrays.stream(HaServices.class.getDeclaredFields())
            .filter(f -> f.getName().equals(serviceName))
            .map(Field::getType)
            .findFirst();
    if (res.isPresent()) {
      return (Class<? extends HaService>) res.get();
    } else {
      throw new IllegalArgumentException(
          "No high availability service with name \"" + serviceName + "\" handled by halyard");
    }
  }
}
