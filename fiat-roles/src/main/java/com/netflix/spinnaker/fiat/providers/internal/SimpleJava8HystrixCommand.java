/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.fiat.providers.internal;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import groovy.util.logging.Slf4j;
import java.util.function.Function;
import java.util.function.Supplier;

@Slf4j
class SimpleJava8HystrixCommand<T> extends HystrixCommand<T> {

  private final String groupKey;
  private final String commandKey;

  private final Supplier<T> work;
  private final Function<Throwable, T> fallback;

  public SimpleJava8HystrixCommand(String groupKey, String commandKey, Supplier<T> work) {
    this(groupKey, commandKey, work, (ignored) -> null);
  }

  public SimpleJava8HystrixCommand(
      String groupKey, String commandKey, Supplier<T> work, Function<Throwable, T> fallback) {
    super(
        HystrixCommand.Setter.withGroupKey(toGroupKey(groupKey))
            .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
            .andCommandPropertiesDefaults(createHystrixCommandPropertiesSetter()));
    this.groupKey = groupKey;
    this.commandKey = commandKey;
    this.work = work;
    this.fallback = fallback;
  }

  @Override
  protected T run() throws Exception {
    return work.get();
  }

  protected T getFallback() {
    T fallbackValue = fallback.apply(this.getFailedExecutionException());
    if (fallbackValue == null) {
      return super.getFallback();
    }
    return fallbackValue;
  }

  private static HystrixCommandGroupKey toGroupKey(String name) {
    return HystrixCommandGroupKey.Factory.asKey(name);
  }

  private static HystrixCommandProperties.Setter createHystrixCommandPropertiesSetter() {
    return HystrixCommandProperties.Setter();
  }
}
