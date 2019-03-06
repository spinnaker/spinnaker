/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.google.names;

import com.netflix.frigga.Names;
import com.netflix.spinnaker.clouddriver.google.model.GoogleLabeledResource;
import com.netflix.spinnaker.clouddriver.names.NamingStrategy;
import com.netflix.spinnaker.moniker.Moniker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.Consumer;

@Component
public class GoogleLabeledResourceNamer implements NamingStrategy<GoogleLabeledResource> {
  static String GCE_MONIKER_PREFIX = "spinnaker-moniker-";
  static String APP = GCE_MONIKER_PREFIX + "application";
  static String CLUSTER = GCE_MONIKER_PREFIX + "cluster";
  static String DETAIL = GCE_MONIKER_PREFIX + "detail";
  static String STACK = GCE_MONIKER_PREFIX + "stack";
  static String SEQUENCE = GCE_MONIKER_PREFIX + "sequence";

  @Override
  public String getName() {
    return "gceAnnotations";
  }

  public void applyMoniker(GoogleLabeledResource labeledResource, Moniker moniker) {
    Map<String, String> templateLabels = labeledResource.getLabels();
    setIfPresent(value -> templateLabels.putIfAbsent(APP, value.toLowerCase()), moniker.getApp());
    setIfPresent(value -> templateLabels.putIfAbsent(CLUSTER, value.toLowerCase()), moniker.getCluster());
    setIfPresent(value -> templateLabels.putIfAbsent(DETAIL, value.toLowerCase()), moniker.getDetail());
    setIfPresent(value -> templateLabels.putIfAbsent(STACK, value.toLowerCase()), moniker.getStack());
    setIfPresent(value -> templateLabels.put(SEQUENCE, value),
      moniker.getSequence() != null ? moniker.getSequence().toString() : null); // Always overwrite sequence
  }

  @Override
  public Moniker deriveMoniker(GoogleLabeledResource labeledResource) {
    String name = labeledResource.getName();
    Names parsed = Names.parseName(name);

    Moniker moniker = Moniker.builder()
      .app(parsed.getApp())
      .cluster(parsed.getCluster())
      .detail(parsed.getDetail())
      .stack(parsed.getStack())
      .sequence(parsed.getSequence())
      .build();

    Map<String, String> labels = labeledResource.getLabels();
    if (moniker.getApp() != null && labels != null) {
      setIfPresent(moniker::setApp, labels.get(APP));
      String cluster = labels.get(CLUSTER);
      String stack = labels.get(STACK);
      String detail = labels.get(DETAIL);
      String sequence = labels.get(SEQUENCE);
      if (cluster == null && (detail != null || stack != null)) {
        // If detail or stack is set and not cluster, we generate the cluster name using frigga convention (app-stack-detail)
        cluster = getClusterName(moniker.getApp(), stack, detail);
      }
      setIfPresent(moniker::setStack, stack);
      setIfPresent(moniker::setDetail, detail);
      setIfPresent(moniker::setCluster, cluster);
      setIfPresent(moniker::setSequence, sequence != null ? Integer.parseInt(sequence) : null);
    }
    return moniker;
  }

  private static String getClusterName(String app, String stack, String detail) {
    StringBuilder sb = new StringBuilder(app);
    if (StringUtils.isNotEmpty(stack)) {
      sb.append("-").append(stack);
    }
    if (StringUtils.isEmpty(stack) && StringUtils.isNotEmpty(detail)) {
      sb.append("-");
    }
    if (StringUtils.isNotEmpty(detail)) {
      sb.append("-").append(detail);
    }
    return sb.toString();
  }

  private static <T> void setIfPresent(Consumer<T> setter, T value) {
    if (value != null) {
      setter.accept(value);
    }
  }
}
