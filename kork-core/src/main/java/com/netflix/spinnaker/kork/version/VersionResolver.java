/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.kork.version;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.core.Ordered;

/** Defines a strategy for resolving a service version. */
public interface VersionResolver extends Ordered {

  /** Returns a service version, if one could be resolved. */
  @Nullable
  String resolve(@Nonnull String serviceName);
}
