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
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.bakery.job.BakeJobBuilder
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ContextConfiguration
import static com.netflix.spinnaker.orca.test.Network.isReachable
import static org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS

@IgnoreIf({ !isReachable("http://bakery.test.netflix.net:7001") })
@ContextConfiguration(classes = [BakeryConfiguration, BatchTestConfiguration])
@DirtiesContext(classMode = AFTER_CLASS)
class OrcaSmokeSpec extends Specification {

  @Autowired JobLauncher jobLauncher
  @Autowired BakeJobBuilder bakeJobBuilder
  @Autowired JobBuilderFactory jobs

  def "can bake and monitor to completion"() {
    given:
    def jobBuilder = jobs.get("${getClass().simpleName}Job")
    def job = bakeJobBuilder.build(jobBuilder).build()

    and:
    def jobParameters = new JobParametersBuilder()
      .addString("region", "us-west-1")
      .addString("bake.user", "rfletcher")
      .addString("bake.package", "oort")
      .addString("bake.baseOs", "ubuntu")
      .addString("bake.baseLabel", "release")
      .toJobParameters()

    when:
    def jobStatus = jobLauncher.run(job, jobParameters).status

    then:
    jobStatus == BatchStatus.COMPLETED
  }

}

