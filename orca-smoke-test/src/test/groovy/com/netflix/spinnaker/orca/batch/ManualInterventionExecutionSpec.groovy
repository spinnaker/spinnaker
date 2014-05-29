package com.netflix.spinnaker.orca.batch

import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.Task
import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobBuilder

import static com.netflix.spinnaker.orca.TaskResult.Status.SUCCEEDED
import static com.netflix.spinnaker.orca.TaskResult.Status.SUSPENDED

class ManualInterventionExecutionSpec extends BatchExecutionSpec {

    def preInterventionTask = Stub(Task)
    def postInterventionTask = Mock(Task)
    def finalTask = Mock(Task)

    def "workflow will stop if the first task suspends the job"() {
        given:
        preInterventionTask.execute(_) >> new DefaultTaskResult(SUSPENDED)

        when:
        launchJob()

        then:
        0 * postInterventionTask._
        0 * finalTask._
    }

    def "workflow will resume if the job is restarted"() {
        given:
        preInterventionTask.execute(_) >> new DefaultTaskResult(SUSPENDED)
        def jobExecution = launchJob()

        when:
        resumeJob jobExecution

        then:
        1 * postInterventionTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
        1 * finalTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    }

    def "can run to completion if the first step does not stop the job"() {
        given:
        preInterventionTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)

        when:
        launchJob()

        then:
        1 * postInterventionTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
        1 * finalTask.execute(_) >> new DefaultTaskResult(SUCCEEDED)
    }

    @Override
    protected Job configureJob(JobBuilder jobBuilder) {
        def step1 = steps.get("PreInterventionStep")
            .tasklet(TaskTaskletAdapter.decorate(preInterventionTask))
            .build()
        def step2 = steps.get("PostInterventionStep")
            .tasklet(TaskTaskletAdapter.decorate(postInterventionTask))
            .build()
        def step3 = steps.get("FinalStep")
            .tasklet(TaskTaskletAdapter.decorate(finalTask))
            .build()
        jobBuilder.start(step1)
            .on(ExitStatus.STOPPED.exitCode).stopAndRestart(step2)
            .from(step1)
            .on(ExitStatus.COMPLETED.exitCode).to(step2)
            .next(step3)
            .build().build()
    }
}
