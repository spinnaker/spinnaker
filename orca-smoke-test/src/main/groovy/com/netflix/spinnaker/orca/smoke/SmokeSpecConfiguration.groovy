package com.netflix.spinnaker.orca.smoke

import com.netflix.spinnaker.orca.bakery.job.BakeJobBuilder
import groovy.transform.CompileStatic
import org.springframework.batch.core.Job
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
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
    private BakeJobBuilder bakeJobBuilder

    @Bean
    Job job() {
        bakeJobBuilder.build(jobs.get("BakeJob")).build()
    }

    @Bean
    JobLauncherTestUtils jobLauncherTestUtils() {
        new JobLauncherTestUtils()
    }
}
