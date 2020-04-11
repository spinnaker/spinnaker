/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.spinnaker.kork.version

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.context.ApplicationContext
import spock.lang.Specification

class SpringPackageVersionResolverSpec extends Specification {

  TestApp testApp = Mock(TestApp)
  ApplicationContext applicationContext = Mock(ApplicationContext)
  SpringPackageVersionResolver subject = new SpringPackageVersionResolver(applicationContext)

  void "Resolves a version"() {
    given:
    Map<String, Object> annotatedBeans = ["TestApp": testApp]
    applicationContext.getBeansWithAnnotation(SpringBootApplication.class) >> annotatedBeans

    when:
    subject.resolve("kork")

    //Mocking the calls to get the package and implementation version is not possible,
    //but this test at least verifies the signature and the call to resolve version.
    then:
    noExceptionThrown()
  }
}

class TestApp {}
