package com.netflix.spinnaker.orca.smoke

import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.config.BakeryConfiguration
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import groovy.transform.CompileStatic
import org.springframework.batch.core.BatchStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.scope.context.ChunkContext
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
            .toJobParameters()

        when:
        def jobStatus = jobLauncherTestUtils.launchJob(jobParameters).status

        then:
        jobStatus == BatchStatus.COMPLETED
    }

}

@Configuration
@Import(BatchTestConfiguration)
@CompileStatic
class SmokeSpecConfiguration {

    @Autowired
    private JobBuilderFactory jobs

    @Autowired
    StepBuilderFactory steps

    @Autowired
    BakeryService bakery

    @Bean
    Job job() {
        def step1 = steps.get("ConfigureBake")
            .tasklet({ StepContribution contribution, ChunkContext chunkContext ->
            chunkContext.stepContext.stepExecution.jobExecution.executionContext.with {
                putString("bake.user", "rfletcher")
                putString("bake.package", "oort")
                putString("bake.baseOs", "ubuntu")
                putString("bake.baseLabel", "release")
            }
        })
            .build()
        def step2 = steps.get("CreateBakeStep")
            .tasklet(new CreateBakeTask(bakery: bakery))
            .build()
        def step3 = steps.get("MonitorBakeStep")
            .tasklet(new MonitorBakeTask(bakery: bakery))
            .build()
        jobs.get("BakeJob")
            .start(step1)
            .next(step2)
            .next(step3)
            .build()
    }

    @Bean
    JobLauncherTestUtils jobLauncherTestUtils() {
        new JobLauncherTestUtils()
    }
}
