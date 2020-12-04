/*
 * Copyright 2020 Adevinta, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.pipeline.expressions.functions

import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException
import org.yaml.snakeyaml.constructor.ConstructorException
import spock.lang.Specification
import spock.lang.Unroll

class UrlExpressionFunctionProviderSpec extends Specification {

  @Unroll
  def "should read yaml"() {
    expect:
    UrlExpressionFunctionProvider.readYaml(currentYaml) == expectedYaml

    where:
    currentYaml               || expectedYaml
    "a: 1\nb: 2\n"            || [a: 1, b: 2]
    "---\na: 1\nb: 2\n"       || [a: 1, b: 2]
  }

  def "should raise exception on multi-doc yaml"() {
    when:
    UrlExpressionFunctionProvider.readYaml("a: 1\nb: 2\n---\nc: 3\n")

    then:
      thrown(SpelHelperFunctionException)
  }

  @Unroll
  def "should read multi-doc yaml"() {
    expect:
    UrlExpressionFunctionProvider.readAllYaml(currentYaml) == expectedYaml

    where:
    currentYaml               || expectedYaml
    "a: 1\nb: 2\n"            || [[a: 1, b: 2]]
    "---\na: 1\nb: 2\n"       || [[a: 1, b: 2]]
    "a: 1\nb: 2\n---\nc: 3\n" || [[a: 1, b: 2],[c: 3]]
  }

  def "should restrict yaml tag usage"() {
    when:
    UrlExpressionFunctionProvider.readAllYaml("!!java.io.FileInputStream [/dev/null]")

    then:
    SpelHelperFunctionException e1 = thrown()
    e1.cause.message.startsWith('could not determine a constructor for the tag tag:yaml.org,2002:java.io.FileInputStream')

    when:
    UrlExpressionFunctionProvider.readYaml("!!java.io.FileInputStream [/dev/null]")

    then:
    SpelHelperFunctionException e2 = thrown()
    e2.cause.message.startsWith('could not determine a constructor for the tag tag:yaml.org,2002:java.io.FileInputStream')
  }
}
