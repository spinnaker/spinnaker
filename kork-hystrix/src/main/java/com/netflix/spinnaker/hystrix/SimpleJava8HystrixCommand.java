/*
 * Copyright 2018 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.hystrix;

import com.netflix.hystrix.HystrixCommand;
import com.netflix.hystrix.HystrixCommandGroupKey;
import com.netflix.hystrix.HystrixCommandKey;
import com.netflix.hystrix.HystrixCommandProperties;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.annotation.Nullable;

public class SimpleJava8HystrixCommand<T> extends HystrixCommand<T> {

  private final Supplier<T> work;
  private final Function<Throwable, T> fallback;

  public SimpleJava8HystrixCommand(String groupKey, String commandKey, Supplier<T> work) {
    this(groupKey, commandKey, work, null);
  }

  public SimpleJava8HystrixCommand(
      String groupKey,
      String commandKey,
      Supplier<T> work,
      @Nullable Function<Throwable, T> fallback) {
    super(
        HystrixCommand.Setter.withGroupKey(HystrixCommandGroupKey.Factory.asKey(groupKey))
            .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
            .andCommandPropertiesDefaults(HystrixCommandProperties.Setter()));
    this.work = work;
    this.fallback = fallback != null ? fallback : (ignored) -> null;
  }

  @Override
  protected T run() throws Exception {
    return work.get();
  }

  @Override
  protected T getFallback() {
    T fallbackValue = fallback.apply(this.getFailedExecutionException());
    if (fallbackValue == null) {
      return super.getFallback();
    }
    return fallbackValue;
  }
}
