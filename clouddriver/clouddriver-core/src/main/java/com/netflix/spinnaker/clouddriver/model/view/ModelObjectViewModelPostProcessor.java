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

package com.netflix.spinnaker.clouddriver.model.view;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface ModelObjectViewModelPostProcessor<T> {
  static <R> R applyExtensionsToObject(
      Optional<List<ModelObjectViewModelPostProcessor<R>>> extensions, R object) {
    return extensions
        .map(exts -> exts.stream().filter(ext -> ext.supports(object)).collect(Collectors.toList()))
        .filter(exts -> !exts.isEmpty())
        .map(
            exts -> {
              for (ModelObjectViewModelPostProcessor<R> extension : exts) {
                extension.process(object);
              }
              return object;
            })
        .orElse(object);
  }

  static <R> Collection<R> applyExtensions(
      Optional<List<ModelObjectViewModelPostProcessor<R>>> extensions, Collection<R> objects) {
    return extensions
        .map(
            ext ->
                (Collection<R>)
                    objects.stream()
                        .map(o -> applyExtensionsToObject(extensions, o))
                        .collect(Collectors.toList()))
        .orElse(objects);
  }

  boolean supports(T instance);

  void process(T model);
}
