/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.clouddriver.cloudrun.op.manifest;

import com.netflix.spinnaker.clouddriver.cloudrun.deploy.exception.CloudrunOperationException;
import com.netflix.spinnaker.clouddriver.cloudrun.model.CloudrunLoadBalancer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Edits a Cloud Run (Knative) service manifest held as a free-form map. Everything the user wrote
 * is preserved; only the fields Spinnaker owns are overwritten.
 */
public final class CloudrunManifestEditor {

  private static final String SPINNAKER_APPLICATION_ANNOTATION = "spinnaker/application";

  private static final String LOCATION_LABEL = "cloud.googleapis.com/location";

  private CloudrunManifestEditor() {}

  /** Deploys pass a single manifest to {@code gcloud run services replace}. */
  public static void requireExactlyOne(List<?> manifests) {
    if (manifests == null || manifests.size() != 1) {
      throw new CloudrunOperationException(
          "A Cloud Run deploy requires exactly one manifest with metadata and spec but found "
              + (manifests == null ? 0 : manifests.size()));
    }
  }

  /** Overwrites the service name, revision name, application annotation and traffic. */
  public static void applySpinnakerFields(
      Map<String, Object> manifest,
      String application,
      String serviceName,
      String revisionName,
      CloudrunLoadBalancer loadBalancer) {
    Map<String, Object> metadata = childMap(manifest, "metadata");
    Map<String, Object> spec = childMap(manifest, "spec");
    metadata.put("name", serviceName);
    childMap(metadata, "annotations").put(SPINNAKER_APPLICATION_ANNOTATION, application);
    childMap(childMap(spec, "template"), "metadata").put("name", revisionName);
    if (loadBalancer != null) {
      insertTrafficPercent(spec, loadBalancer);
    }
  }

  /** The region named by the manifest's {@code cloud.googleapis.com/location} label, if any. */
  @SuppressWarnings("unchecked")
  public static Optional<String> locationLabel(Map<String, Object> manifest) {
    Object labels = childMap(manifest, "metadata").get("labels");
    if (labels instanceof Map) {
      Object location = ((Map<String, Object>) labels).get(LOCATION_LABEL);
      if (location != null) {
        return Optional.of(location.toString());
      }
    }
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> containers(Map<String, Object> manifest) {
    Object spec = manifest.get("spec");
    Object template = spec instanceof Map ? ((Map<String, Object>) spec).get("template") : null;
    Object templateSpec =
        template instanceof Map ? ((Map<String, Object>) template).get("spec") : null;
    Object containers =
        templateSpec instanceof Map ? ((Map<String, Object>) templateSpec).get("containers") : null;
    List<Map<String, Object>> result = new ArrayList<>();
    if (containers instanceof List) {
      for (Object container : (List<Object>) containers) {
        if (container instanceof Map) {
          result.add((Map<String, Object>) container);
        }
      }
    }
    return result;
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> childMap(Map<String, Object> parent, String key) {
    Object child = parent.get(key);
    if (child == null) {
      child = new LinkedHashMap<String, Object>();
      parent.put(key, child);
    }
    if (!(child instanceof Map)) {
      throw new IllegalArgumentException(
          String.format("Expected '%s' in the manifest to be an object", key));
    }
    return (Map<String, Object>) child;
  }

  private static void insertTrafficPercent(Map<String, Object> spec, CloudrunLoadBalancer lb) {
    if (lb.getSplit() == null || lb.getSplit().getTrafficTargets() == null) {
      return;
    }
    List<Map<String, Object>> trafficTargets = new ArrayList<>();
    lb.getSplit()
        .getTrafficTargets()
        .forEach(
            trafficTarget -> {
              Map<String, Object> existingTrafficMap = new LinkedHashMap<>();
              existingTrafficMap.put("percent", trafficTarget.getPercent());
              existingTrafficMap.put("revisionName", trafficTarget.getRevisionName());
              trafficTargets.add(existingTrafficMap);
            });
    spec.put("traffic", trafficTargets);
  }
}
