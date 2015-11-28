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


package com.netflix.spinnaker.hystrix

import com.netflix.hystrix.exception.HystrixRuntimeException
import spock.lang.Specification
import spock.lang.Unroll

class SimpleHystrixCommandSpec extends Specification {
  @Unroll
  def "should raise a HystrixRuntimeException if no suitable (non-null returning) fallback is available"() {
    when:
    new ExampleCommand().run()

    then:
    HystrixRuntimeException e = thrown()
    e.failureType == HystrixRuntimeException.FailureType.COMMAND_EXCEPTION

    where:
    command                                                            || _
    new ExampleCommand(fallback: { throw new NullPointerException() }) || _
  }

  def "should return non-null fallback value without exception"() {
    expect:
    new ExampleCommand(fallback: { "fallback value" }).run() == "fallback value"
  }

  static class ExampleCommand {
    Closure fallback = null

    public String run() {
      new SimpleHystrixCommand<String>("example", "run", {
        throw new IllegalStateException()
      }, fallback).execute()
    }
  }
}
