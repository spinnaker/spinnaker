/*
 * Copyright 2020 Expedia, Inc.
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
 *
 */

package com.netflix.spinnaker.clouddriver.ecs.names;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.moniker.Moniker;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;

public class MonikerHelper {

  public static String getClusterName(String appName, String stack, String detail) {
    stack = Objects.toString(stack, "");

    if (StringUtils.isNotEmpty(detail)) {
      return String.join("-", appName, stack, detail);
    }

    if (StringUtils.isNotEmpty(stack)) {
      return String.join("-", appName, stack);
    }

    return appName;
  }

  public static Moniker applicationNameToMoniker(String appName) {
    Names names = Names.parseName(appName);
    return Moniker.builder()
        .app(names.getApp())
        .stack(names.getStack())
        .detail(names.getDetail())
        .cluster(names.getCluster())
        .sequence(names.getSequence())
        .build();
  }
}
