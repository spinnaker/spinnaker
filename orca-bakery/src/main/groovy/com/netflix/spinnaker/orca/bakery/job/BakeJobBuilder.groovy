package com.netflix.spinnaker.orca.bakery.job

import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.bakery.tasks.CreateBakeTask
import com.netflix.spinnaker.orca.bakery.tasks.MonitorBakeTask
import groovy.transform.CompileStatic
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.job.builder.SimpleJobBuilder
import org.springframework.batch.core.step.tasklet.Tasklet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import static com.netflix.spinnaker.orca.batch.TaskTaskletAdapter.decorate

@Component
@CompileStatic
class BakeJobBuilder {

    private ApplicationContext applicationContext
    private StepBuilderFactory steps

    SimpleJobBuilder build(JobBuilder jobBuilder) {
        def step1 = steps.get("CreateBakeStep")
            .tasklet(buildTask(CreateBakeTask))
            .build()
        def step2 = steps.get("MonitorBakeStep")
            .tasklet(buildTask(MonitorBakeTask))
            .build()
        jobBuilder
            .start(step1)
            .next(step2)
    }

    private Tasklet buildTask(Class<? extends Task> taskType) {
        def task = taskType.newInstance()
        autowire task
        decorate task
    }

    // TODO: great candidate for a trait
    void autowire(obj) {
        applicationContext.autowireCapableBeanFactory.autowireBean(obj)
    }

    @Autowired
    void setSteps(StepBuilderFactory steps) {
        this.steps = steps
    }

    @Autowired
    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }
}
