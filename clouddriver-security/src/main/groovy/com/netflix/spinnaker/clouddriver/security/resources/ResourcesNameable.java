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

package com.netflix.spinnaker.clouddriver.security.resources;

import com.netflix.frigga.Names;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Convenience trait for extracting application names from operation descriptions that have multiple
 * items conforming to the Frigga naming conventions. Examples include load balancers and instances.
 */
public interface ResourcesNameable {
  Collection<String> getNames();

  default Collection<String> getResourceApplications() {
    return Optional.ofNullable(getNames()).orElse(Collections.emptyList()).stream()
        .filter(Objects::nonNull)
        .map(name -> Names.parseName(name).getApp())
        .collect(Collectors.toList());
  }
}
