package com.netflix.spinnaker.orca.smoke

import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import groovy.transform.CompileStatic
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration
import spock.lang.IgnoreIf
import spock.lang.Specification

import static com.netflix.spinnaker.orca.test.Network.isReachable

@IgnoreIf({ !isReachable("http://bakery.test.netflix.net:7001") })
@ContextConfiguration(classes = [BakeryConfiguration, SmokeSpecConfiguration])
class OrcaSmokeSpec extends Specification {

    @Autowired
    JobLauncherTestUtils jobLauncherTestUtils

    def "can bake and monitor to completion"() {
        given:
        def jobParameters = new JobParametersBuilder()
            .addString("region", "us-west-1")
            .addString("bake.user", "rfletcher")
            .addString("bake.package", "oort")
            .addString("bake.baseOs", "ubuntu")
            .addString("bake.baseLabel", "release")
            .toJobParameters()

        when:
        def jobStatus = jobLauncherTestUtils.launchJob(jobParameters).status

        then:
        jobStatus == BatchStatus.COMPLETED
    }

}

