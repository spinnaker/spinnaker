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

import java.util.concurrent.Callable
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

  public static <T> ListCommand newListCommand(String groupKey,
                                           String commandKey,
                                           Callable<List<T>> work,
                                           Callable<List<T>> fallback = { null }) {
    new ListCommand(groupKey, commandKey, propagate(work, false), fallback)
  }

  public static <K, V> MapCommand<K, V> newMapCommand(String groupKey,
                                         String commandKey,
                                         Callable<Map<K, V>> work,
                                         Callable<Map<K, V>> fallback = { null }) {
    new MapCommand(groupKey, commandKey, propagate(work, false), fallback)
  }

  public static StringCommand newStringCommand(String groupKey,
                                        String commandKey,
                                        Callable<String> work,
                                        Callable<String> fallback = { null }) {

    new StringCommand(groupKey, commandKey, propagate(work, false), fallback )
  }

  public static VoidCommand newVoidCommand(String groupKey,
                                        String commandKey,
                                        Callable<Void> work,
                                        Callable<Void> fallback = { null }) {

    new VoidCommand(groupKey, commandKey, propagate(work, false), fallback )
  }

  static class ListCommand<T> extends AbstractHystrixCommand<List<T>> {
    ListCommand(String groupKey, String commandKey, Callable<List<T>> work, Callable<List<T>> fallback) {
      super(groupKey, commandKey, work, fallback)
    }
  }

  static class MapCommand<K, V> extends AbstractHystrixCommand<Map<K, V>> {
    MapCommand(String groupKey, String commandKey, Callable<Map<K, V>> work, Callable<Map<K, V>> fallback) {
      super(groupKey, commandKey, work, fallback)
    }
  }

  static class StringCommand extends AbstractHystrixCommand<String> {
    StringCommand(String groupKey, String commandKey, Callable<String> work, Callable<String> fallback) {
      super(groupKey, commandKey, work, fallback)
    }
  }

  static class VoidCommand extends AbstractHystrixCommand<Void> {
    VoidCommand(String groupKey, String commandKey, Callable<Void> work, Callable<Void> fallback) {
      super(groupKey, commandKey, work, fallback)
    }
  }
}
