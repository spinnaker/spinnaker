/*
 * Copyright 2016 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.security.sdkclient;

import com.google.common.cache.Cache;
import com.netflix.spectator.api.Registry;

public class LoadingCacheMetrics {
  public static void instrument(String prefix, Registry registry, Cache<?, ?> cache) {
    registry.gauge(prefix + ".averageLoadPenalty", cache, (c) -> c.stats().averageLoadPenalty());
    registry.gauge(prefix + ".evictionCount", cache, (c) -> c.stats().evictionCount());
    registry.gauge(prefix + ".hitCount", cache, (c) -> c.stats().hitCount());
    registry.gauge(prefix + ".hitRate", cache, (c) -> c.stats().hitRate());
    registry.gauge(prefix + ".loadCount", cache, (c) -> c.stats().loadCount());
    registry.gauge(prefix + ".loadExceptionCount", cache, (c) -> c.stats().loadExceptionCount());
    registry.gauge(prefix + ".loadExceptionRate", cache, (c) -> c.stats().loadExceptionRate());
    registry.gauge(prefix + ".loadSuccessCount", cache, (c) -> c.stats().loadSuccessCount());
    registry.gauge(prefix + ".missCount", cache, (c) -> c.stats().missCount());
    registry.gauge(prefix + ".missRate", cache, (c) -> c.stats().missRate());
    registry.gauge(prefix + ".requestCount", cache, (c) -> c.stats().requestCount());
    registry.gauge(prefix + ".totalLoadTime", cache, (c) -> c.stats().totalLoadTime());
  }
}
