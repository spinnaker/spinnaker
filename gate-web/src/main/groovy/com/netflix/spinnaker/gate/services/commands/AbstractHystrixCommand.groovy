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

import com.netflix.hystrix.HystrixCommand
import com.netflix.hystrix.HystrixCommandKey
import groovy.transform.CompileStatic


import static com.netflix.spinnaker.gate.services.commands.HystrixFactory.createHystrixCommandPropertiesSetter
import static com.netflix.spinnaker.gate.services.commands.HystrixFactory.toGroupKey

@CompileStatic
abstract class AbstractHystrixCommand<T> extends HystrixCommand<T> {

  protected final boolean withLastKnownGoodFallback
  protected final Closure work
  protected T lastKnownGood

  public AbstractHystrixCommand(String groupKey, String commandKey, boolean withLastKnownGoodFallback, T defaultValue,
                                Closure work) {
    super(HystrixCommand.Setter.withGroupKey(toGroupKey(groupKey))
        .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
        .andCommandPropertiesDefaults(createHystrixCommandPropertiesSetter()
        .withExecutionIsolationThreadTimeoutInMilliseconds(60000)))
    this.withLastKnownGoodFallback = withLastKnownGoodFallback
    this.lastKnownGood = defaultValue
    this.work = work
  }

  @Override
  protected T run() throws Exception {
    def result = work()
    if (withLastKnownGoodFallback) {
      lastKnownGood = result
    }
    result
  }

  protected T getFallback() {
    withLastKnownGoodFallback ? lastKnownGood : null
  }
}
