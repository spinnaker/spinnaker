/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.telemetry


import com.netflix.spectator.api.DefaultRegistry
import com.netflix.spectator.api.Registry
import spock.lang.Shared
import spock.lang.Specification

class InstrumentedProxySpec extends Specification {

  @Shared
  Registry registry = new DefaultRegistry()

  MyContract subject = InstrumentedProxy.proxy(registry, new MyContractImpl(), "myns")

  def "should generate method metrics on init"() {
    when:
    def ignored = subject.ignored()
    def result = subject.doStuff()

    then:
    ignored == "so sad"
    result == "did stuff"
  }

  def "should preserve the exception class"() {
    when:
    subject.throwError()

    then:
    thrown MyException
  }
}

interface MyContract {

  String doStuff()

  @Instrumented(ignore = true)
  String ignored();

  String sig1(String p1);

  @Instrumented(metricName = "sig1Long", tags = ["foo", "bar"])
  String sig1(Long p1);

  void throwError()
}

class MyContractImpl implements MyContract {

  @Override
  String doStuff() {
    return "did stuff"
  }

  @Override
  String ignored() {
    return "so sad"
  }

  @Override
  String sig1(String p1) {
    return null
  }

  @Override
  String sig1(Long p1) {
    return null
  }

  @Override
  void throwError() {
    throw new MyException()
  }
}

class MyException extends RuntimeException {}
