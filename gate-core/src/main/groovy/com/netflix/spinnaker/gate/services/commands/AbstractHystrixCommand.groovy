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
import com.netflix.hystrix.HystrixCommand
import com.netflix.hystrix.HystrixCommandKey
import com.netflix.spinnaker.gate.retrofit.UpstreamBadRequest
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.springframework.http.HttpStatus
import retrofit.RetrofitError
import static HystrixFactory.createHystrixCommandPropertiesSetter
import static HystrixFactory.toGroupKey

@Slf4j
@CompileStatic
abstract class AbstractHystrixCommand<T> extends HystrixCommand<T> {

  private final String groupKey
  private final String commandKey

  protected final Callable<T> work
  protected final Callable<T> fallback

  public AbstractHystrixCommand(String groupKey,
                                String commandKey,
                                Callable<T> work,
                                Callable<T> fallback) {
    super(HystrixCommand.Setter.withGroupKey(toGroupKey(groupKey))
        .andCommandKey(HystrixCommandKey.Factory.asKey(commandKey))
        .andCommandPropertiesDefaults(createHystrixCommandPropertiesSetter()))
    this.groupKey = groupKey
    this.commandKey = commandKey
    this.work = work
    this.fallback = fallback ?: { null }
  }

  @Override
  protected T run() throws Exception {
    try {
      return work.call()
    } catch (RetrofitError error) {
      throw UpstreamBadRequest.classifyError(error, HttpStatus.values().findAll { it.is4xxClientError() }*.value() as Collection<Integer>)
    }
  }

  protected T getFallback() {
    return fallback.call()
  }

  @Override
  T execute() {
    def result = super.execute() as T
    if (result == null && isResponseFromFallback()) {
      def e = getFailedExecutionException()
      def errorMessage = "Fallback encountered"
      if (e instanceof RetrofitError) {
        def retrofitError = (RetrofitError) e
        errorMessage += " (url: ${retrofitError.url}, type: ${retrofitError.kind}, status: ${retrofitError.response?.status})"
      }
      log.error(errorMessage, e)
      throw new ThrottledRequestException("No fallback available (group: '${groupKey}', command: '${commandKey}', exception: '${e?.toString() ?: ""}')", e)
    }

    return result
  }
}
