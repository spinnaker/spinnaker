/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.igor.history.model;

import java.util.HashMap;
import java.util.Map;

/** TODO(rz): Documnt. */
public interface BuildEvent<T extends BuildContent> extends Event {

  T getContent();

  default Map<?, ?> getDetails() {
    Map<String, String> d = new HashMap<>();
    d.put("type", "build");
    d.put("source", "igor");
    return d;
  }
}
