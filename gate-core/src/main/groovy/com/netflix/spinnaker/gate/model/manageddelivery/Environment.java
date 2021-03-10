/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.gate.model.manageddelivery;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.Data;

@Data
public class Environment {
  String name;
  Collection<Resource> resources;
  Collection<Map<String, Object>> constraints;
  Collection<Notification> notifications;
  Map<String, Object> locations;
  List<Map<String, Object>> verifyWith;
}
