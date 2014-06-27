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

package com.netflix.spinnaker.orca.smoke

import spock.lang.IgnoreIf
import spock.lang.Specification
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.api.JobStarter
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.config.OrcaConfiguration
import com.netflix.spinnaker.orca.test.batch.BatchTestConfiguration
import org.springframework.batch.core.BatchStatus
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import static com.netflix.spinnaker.orca.test.net.Network.isReachable
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS

@IgnoreIf({ !isReachable("http://bakery.test.netflix.net:7001") })
@ContextConfiguration(classes = [BakeryConfiguration, BatchTestConfiguration])
@DirtiesContext(classMode = AFTER_CLASS)
class OrcaSmokeSpec extends Specification {

  @Autowired JobStarter jobStarter
  @Autowired ObjectMapper mapper

  def "can bake and monitor to completion"() {
    given:
    def configJson = mapper.writeValueAsString(config)

    when:
    def jobStatus = jobStarter.start(configJson).status

    then:
    jobStatus == BatchStatus.COMPLETED

    where:
    config = [[
        type            : "bake",
        region          : "us-west-1",
        "bake.user"     : "rfletcher",
        "bake.package"  : "oort",
        "bake.baseOs"   : "ubuntu",
        "bake.baseLabel": "release"
    ]]
  }
}

