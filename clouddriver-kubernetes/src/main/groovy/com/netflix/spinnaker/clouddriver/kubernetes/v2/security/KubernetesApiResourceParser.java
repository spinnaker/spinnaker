/*
 * Copyright 2019 Armory
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

package com.netflix.spinnaker.clouddriver.kubernetes.v2.security;

import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesApiGroup;
import com.netflix.spinnaker.clouddriver.kubernetes.v2.description.manifest.KubernetesKind;
import java.util.HashSet;
import java.util.Set;

public class KubernetesApiResourceParser {

  public static Set<KubernetesKind> parse(String input) {
    String[] lines = input.trim().split("\n");
    String headerRow = lines[0];
    int nameIndex = headerRow.indexOf("NAME");
    int apiGroupIndex = headerRow.indexOf("APIGROUP");
    int namespaceIndex = headerRow.indexOf("NAMESPACED");
    int kindIndex = headerRow.indexOf("KIND");

    // we expect NAME to be at index 0 of the first row
    // if it isn't, then we didn't get the data in the
    // format we expected
    if (nameIndex != 0) {
      throw new IllegalArgumentException(
          "api-resources input not in the proper format. expected to find NAME header.");
    }

    Set<KubernetesKind> kinds = new HashSet<>();

    for (int i = 1; i < lines.length; i++) {
      String line = lines[i];
      String apiGroup = line.substring(apiGroupIndex, namespaceIndex).trim();
      String kind = line.substring(kindIndex).trim();
      kinds.add(KubernetesKind.from(kind, KubernetesApiGroup.fromString(apiGroup)));
    }

    return kinds;
  }
}
