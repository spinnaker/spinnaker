/*
 * Copyright 2019 Armory, Inc.
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

package com.netflix.spinnaker.rosco.manifests.kustomize.mapping;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;

@Data
public class Kustomization {

  private List<String> resources = new ArrayList<>();

  private List<ConfigMapGenerator> configMapGenerator = new ArrayList<>();

  private List<String> crds = new ArrayList<>();

  private List<String> generators = new ArrayList<>();

  private List<Patch> patches = new ArrayList<>();

  private List<String> patchesStrategicMerge = new ArrayList<>();

  private List<PatchesJson6902> patchesJson6902 = new ArrayList<>();

  /** Self reference is the artifact reference which sourced this Kustomization */
  private String selfReference;

  @JsonIgnore private Map<String, Object> additionalProperties = new HashMap<>();

  public Set<String> getFilesToDownload() {
    HashSet<String> toEvaluate = new HashSet<>();
    toEvaluate.addAll(crds);
    toEvaluate.addAll(generators);
    toEvaluate.addAll(patchesStrategicMerge);
    toEvaluate.addAll(patches.stream().map(Patch::getPath).collect(Collectors.toList()));
    toEvaluate.addAll(
        patchesJson6902.stream().map(PatchesJson6902::getPath).collect(Collectors.toList()));
    toEvaluate.addAll(
        this.configMapGenerator.stream()
            .map(configMapGenerator -> configMapGenerator.getFiles())
            .flatMap(files -> files.stream())
            .collect(Collectors.toSet()));
    return toEvaluate;
  }

  public Set<String> getFilesToEvaluate() {
    HashSet<String> toDownload = new HashSet<>();
    toDownload.addAll(this.getResources());
    return toDownload;
  }
}
