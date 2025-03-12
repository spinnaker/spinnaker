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
package com.netflix.spinnaker.kork.dynamicconfig

import org.springframework.core.env.Environment
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static com.netflix.spinnaker.kork.dynamicconfig.ScopedCriteria.*

class SpringDynamicConfigServiceSpec extends Specification {

  Environment environment = Mock()

  @Subject
  SpringDynamicConfigService subject = new SpringDynamicConfigService()

  @Unroll
  def "should return correctly chained feature flag"() {
    given:
    environment.getProperty("myfeature.enabled", Boolean) >> { true }
    environment.getProperty("myfeature.region.us-west-2", Boolean) >> { false }
    environment.getProperty("myfeature.application.orca", Boolean) >> { false }
    subject.setEnvironment(environment)

    expect:
    subject.isEnabled(feature, false, criteria.build()) == expected

    where:
    feature     | criteria                                      || expected
    "noexist"   | new Builder()                                 || false
    "myfeature" | new Builder()                                 || true
    "myfeature" | new Builder().withApplication("clouddriver")  || true
    "myfeature" | new Builder().withApplication("orca")         || false
    "myfeature" | new Builder().withRegion("us-west-2")         || false
    "myfeature" | new Builder().withRegion("us-east-1")         || true
  }

}
