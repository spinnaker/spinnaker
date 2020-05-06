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
 *
 */

package com.netflix.spinnaker.gate.api.extension;

import com.netflix.spinnaker.kork.annotations.Alpha;
import com.netflix.spinnaker.kork.plugins.api.internal.SpinnakerExtensionPoint;
import java.util.Collections;
import javax.annotation.Nonnull;

/**
 * An {@code ExtensionPoint} that allows for a new api to be exposed under a common
 * `/extensions/{id}` path.
 */
@Alpha
public interface ApiExtension extends SpinnakerExtensionPoint {
  @Nonnull
  String id();

  default boolean handles(@Nonnull HttpRequest httpRequest) {
    return false;
  }

  @Nonnull
  default HttpResponse handle(@Nonnull HttpRequest httpRequest) {
    return HttpResponse.of(200, Collections.emptyMap(), "Hello world!");
  }
}
