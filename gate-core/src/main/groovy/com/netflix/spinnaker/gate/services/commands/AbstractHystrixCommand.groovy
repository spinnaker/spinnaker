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
import groovy.util.logging.Slf4j

import static HystrixFactory.createHystrixCommandPropertiesSetter
import static HystrixFactory.toGroupKey

@Slf4j
@CompileStatic
abstract class AbstractHystrixCommand<T> extends HystrixCommand<T> {

  private final String groupKey
  private final String commandKey

  protected final Closure work
  protected final Closure fallback

  public AbstractHystrixCommand(String groupKey,
                                String commandKey,
                                Closure work,
                                Closure fallback) {
    super(HystrixCommand.Setter.withGroupKey(toGroupKey(groupKey))
        .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
        .andCommandPropertiesDefaults(createHystrixCommandPropertiesSetter()
        .withExecutionIsolationThreadTimeoutInMilliseconds(60000)))
    this.groupKey = groupKey
    this.commandKey = commandKey
    this.work = work
    this.fallback = fallback ?: { null }
  }

  @Override
  protected T run() throws Exception {
    return work()
  }

  protected T getFallback() {
    return (fallback.call() as T) ?: null
  }

  @Override
  T execute() {
    def result = super.execute() as T
    if (result == null && isResponseFromFallback()) {
      def e = getFailedExecutionException()
      def eMessage = e?.toString() ?: ""
      log.error("Fallback encountered", e)
      throw new ThrottledRequestException("No fallback available (group: '${groupKey}', command: '${commandKey}', exception: '${eMessage}')", e)
    }

    return result
  }
}
