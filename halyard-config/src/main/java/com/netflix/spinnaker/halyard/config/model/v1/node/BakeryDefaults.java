/*
 * Copyright 2017 Google, Inc.
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
 *
 */

package com.netflix.spinnaker.halyard.config.model.v1.node;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = false)
@Data
public abstract class BakeryDefaults<T extends BaseImage> extends Node {
  private final String nodeName = "bakeryDefaults";

  String templateFile;
  List<T> baseImages = new ArrayList<>();

  @Override
  public NodeIterator getChildren() {
    return NodeIteratorFactory.makeListIterator(
        baseImages.stream().map(b -> (Node) b).collect(Collectors.toList()));
  }

  public void addDefaultImages(List<T> otherBaseImages) {
    Set<String> existingIds =
        baseImages.stream().map(i -> i.getBaseImage().getId()).collect(Collectors.toSet());
    List<T> dedupedOtherBaseImages =
        otherBaseImages.stream()
            .filter(i -> !existingIds.contains(i.getBaseImage().getId()))
            .collect(Collectors.toList());

    baseImages.addAll(dedupedOtherBaseImages);
  }
}
