package com.netflix.spinnaker.orca.smoke

import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import groovy.transform.CompileStatic
import org.springframework.batch.core.Job
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

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
        def step1 = steps.get("CreateBakeStep")
            .tasklet(TaskTaskletAdapter.decorate(new CreateBakeTask(bakery: bakery)))
            .build()
        def step2 = steps.get("MonitorBakeStep")
            .tasklet(TaskTaskletAdapter.decorate(new MonitorBakeTask(bakery: bakery)))
            .build()
        jobs.get("BakeJob")
            .start(step1)
            .next(step2)
            .build()
    }

    @Bean
    JobLauncherTestUtils jobLauncherTestUtils() {
        new JobLauncherTestUtils()
    }
}
