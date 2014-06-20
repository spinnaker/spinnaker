package com.netflix.spinnaker.orca.bakery.job

import com.netflix.spinnaker.orca.bakery.api.BakeryService
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import com.netflix.spinnaker.orca.batch.TaskTaskletAdapter
import groovy.transform.CompileStatic
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.builder.SimpleJobBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class BakeJobBuilder {

    @Autowired
    private StepBuilderFactory steps

    @Autowired
    private BakeryService bakery

    SimpleJobBuilder build(JobBuilder jobBuilder) {
        def step1 = steps.get("CreateBakeStep")
            .tasklet(TaskTaskletAdapter.decorate(new CreateBakeTask(bakery: bakery)))
            .build()
        def step2 = steps.get("MonitorBakeStep")
            .tasklet(TaskTaskletAdapter.decorate(new MonitorBakeTask(bakery: bakery)))
            .build()
        jobBuilder
            .start(step1)
            .next(step2)
    }

    void setSteps(StepBuilderFactory steps) {
        this.steps = steps
    }

    void setBakery(BakeryService bakery) {
        this.bakery = bakery
    }
}
