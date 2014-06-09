package com.netflix.spinnaker.orca

import groovy.transform.CompileStatic
import org.springframework.batch.core.Job
import org.springframework.batch.core.StepContribution
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.scope.context.ChunkContext
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.batch.test.JobLauncherTestUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration
@Import(CassandraRepositoryConfiguration)
@CompileStatic
class SimpleJobConfiguration {

    @Autowired
    private JobBuilderFactory jobs

    @Autowired
    private StepBuilderFactory steps

    @Bean
    Job job() {
        def tasklet = { StepContribution contribution, ChunkContext chunkContext ->
            RepeatStatus.FINISHED
        }
        def step1 = steps.get("SimpleStep")
            .tasklet(tasklet)
            .build()
        jobs.get("SimpleJob")
            .start(step1)
            .build()
    }

    @Bean
    JobLauncherTestUtils jobLauncherTestUtils() {
        new JobLauncherTestUtils()
    }
}
