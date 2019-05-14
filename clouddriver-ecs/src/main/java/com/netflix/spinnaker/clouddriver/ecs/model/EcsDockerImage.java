/*
 * Copyright 2017 Lookout, Inc.
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

package com.netflix.spinnaker.clouddriver.ecs.model;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class EcsDockerImage {
  String region;
  String imageName;
  Map<String, List<String>> amis = new HashMap<>();
  Map<String, Object> attributes = new HashMap<>();

  public void setAttribute(String name, Object attribute) {
    attributes.put(name, attribute);
  }

  public void addAmiForRegion(String region, String ami) {
    if (!amis.containsKey(region)) {
      amis.put(region, new LinkedList<>());
    }
    amis.get(region).add(ami);
  }
}
