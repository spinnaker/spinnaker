/*
 * Copyright 2016 Google, Inc.
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
 */

package com.netflix.spinnaker.halyard.config.spinnaker.v1;

import lombok.Data;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Optional;

@Data
public class BillOfMaterials {
  String version;
  Services services;

  @Data
  public static class Services {
    Component echo;
    Component clouddriver;
    Component deck;
    Component fiat;
    Component front50;
    Component gate;
    Component igor;
    Component orca;
    Component rosco;

    public String getComponentVersion(String componentName) {
      Optional<Field> field = Arrays.stream(Services.class.getDeclaredFields())
          .filter(f -> f.getName().equals(componentName))
          .findFirst();

      if (!field.isPresent()) {
        throw new RuntimeException("No supported spinnaker component named " + componentName);
      }

      try {
        return ((Component) field.get().get(this)).getVersion();
      } catch (IllegalAccessException e) {
        throw new RuntimeException(e);
      } catch (NullPointerException e) {
        throw new RuntimeException("Spinnaker component " + componentName + " is not listed in the BOM");
      }
    }

    @Data
    static class Component {
      String version;
      // TODO(lwander) dependencies will go here.
    }
  }
}
