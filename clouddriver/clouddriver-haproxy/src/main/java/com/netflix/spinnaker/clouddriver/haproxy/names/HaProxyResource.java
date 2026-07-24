/*
 * Copyright 2026 McIntosh.farm
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
package com.netflix.spinnaker.clouddriver.haproxy.names;

import java.util.Map;

/**
 * Common interface for HAProxy configuration objects (frontends, backends) that can be named via
 * Monikers. The generated Data Plane API models cannot implement this directly, so callers wrap
 * them with the object's name and {@code metadata} map.
 */
public interface HaProxyResource {
  String getName();

  /** The Data Plane API {@code metadata} map attached to the configuration object; may be null. */
  Map<String, Object> getMetadata();
}
