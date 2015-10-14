/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.gate.services.commands

import com.netflix.hystrix.HystrixCommandGroupKey
import com.netflix.hystrix.HystrixCommandProperties
import static com.netflix.spinnaker.security.AuthenticatedRequest.propagate

class HystrixFactory {
  static HystrixCommandProperties.Setter createHystrixCommandPropertiesSetter() {
    HystrixCommandProperties.invokeMethod("Setter", null)
  }

  static HystrixCommandGroupKey toGroupKey(String name) {
    HystrixCommandGroupKey.Factory.asKey(name)
  }

  public static ListCommand newListCommand(String groupKey,
                                           String commandKey,
                                           Closure<? extends List> work,
                                           Closure<? extends List> fallback = { null }) {
    new ListCommand(groupKey, commandKey, propagate(work, false), fallback)
  }

  public static MapCommand newMapCommand(String groupKey,
                                         String commandKey,
                                         Closure<? extends Map> work,
                                         Closure<? extends Map> fallback = { null }) {
    new MapCommand(groupKey, commandKey, propagate(work, false), fallback)
  }

  private static class ListCommand extends AbstractHystrixCommand<List> {
    ListCommand(String groupKey, String commandKey, Closure work, Closure fallback) {
      super(groupKey, commandKey, work, fallback)
    }
  }

  private static class MapCommand extends AbstractHystrixCommand<Map> {
    MapCommand(String groupKey, String commandKey, Closure work, Closure fallback) {
      super(groupKey, commandKey, work, fallback)
    }
  }
}
