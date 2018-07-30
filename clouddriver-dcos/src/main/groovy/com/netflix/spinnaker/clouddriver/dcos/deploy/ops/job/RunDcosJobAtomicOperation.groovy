/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.deploy.ops.job

import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.deploy.description.job.RunDcosJobDescription
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerJobId
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import mesosphere.marathon.client.model.v2.LocalVolume
import mesosphere.metronome.client.model.v1.Artifact
import mesosphere.metronome.client.model.v1.Constraint
import mesosphere.metronome.client.model.v1.Docker
import mesosphere.metronome.client.model.v1.Job
import mesosphere.metronome.client.model.v1.JobRunConfiguration
import mesosphere.metronome.client.model.v1.JobSchedule
import mesosphere.metronome.client.model.v1.Placement
import mesosphere.metronome.client.model.v1.RestartPolicy

class RunDcosJobAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "RUN_JOB"

  final DcosClientProvider dcosClientProvider
  final RunDcosJobDescription description

  RunDcosJobAtomicOperation(DcosClientProvider dcosClientProvider, RunDcosJobDescription description) {
    this.dcosClientProvider = dcosClientProvider
    this.description = description
  }

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Override
  DeploymentResult operate(List priorOutputs) {
    def jobName = description.general.id

    task.updateStatus BASE_PHASE, "Initializing creation of job ${jobName}."

    def dcosClient = dcosClientProvider.getDcosClient(description.credentials, description.dcosCluster)
    def job = mapDescriptionToJob(description)

    if (!dcosClient.maybeJob(description.general.id).isPresent()) {
      task.updateStatus BASE_PHASE, "Job with id of ${jobName} does not exist, creating job."

      if (job.schedules != null) {
        dcosClient.createJobWithSchedules(job)
      } else {
        dcosClient.createJob(job)
      }

      task.updateStatus BASE_PHASE, "Job ${jobName} was successfully created."
    } else {
      task.updateStatus BASE_PHASE, "Job with id of ${jobName} already exists, updating job."

      if (job.schedules != null) {
        dcosClient.updateJobWithSchedules(jobName, job)
      } else {
        dcosClient.updateJob(jobName, job)
      }

      task.updateStatus BASE_PHASE, "Job ${jobName} was successfully updated."
    }

    task.updateStatus BASE_PHASE, "Triggering job ${jobName}..."

    def jobRun = dcosClient.triggerJobRun(jobName)

    task.updateStatus BASE_PHASE, "Job ${jobName} has been started."

    // We are kinda hacking our own name together here since applications cannot be "batch" jobs in DC/OS land currently.
    // Stack = Metronome job name
    // Detail = Job mesos task id
    def jobId = new DcosSpinnakerJobId(description.application, jobRun.jobId, jobRun.id)

    // TODO We will want to change location to use groups like apps once that is supported.
    return new DeploymentResult().with {
      deployedNames = [jobId.toString()]
      deployedNamesByLocation[description.dcosCluster] = [jobId.toString()]
      it
    }
  }

  static Job mapDescriptionToJob(RunDcosJobDescription jobDescription) {
    new Job().with {
      id = jobDescription.general.id
      it.description = jobDescription.general.description

      if (jobDescription.labels) {
        labels = jobDescription.labels.clone() as Map<String, String>
      }

      run = new JobRunConfiguration().with {
        cpus = jobDescription.general.cpus
        // TODO - Add GPU back in once it is supported.
        //gpus = description.general.gpus
        mem = jobDescription.general.mem
        disk = jobDescription.general.disk
        cmd = jobDescription.general.cmd

        maxLaunchDelay = jobDescription.maxLaunchDelay
        user = jobDescription.user

        if (jobDescription.constraints) {
          placement = new Placement().with {
            constraints = jobDescription.constraints.collect { constraint ->
              new Constraint().with {
                attribute = constraint.attribute
                operator = constraint.operator
                value = constraint.value
                it
              }
            }
            it
          }
        }

        if (jobDescription.restartPolicy) {
          restart = new RestartPolicy().with {
            activeDeadlineSeconds = jobDescription.restartPolicy.activeDeadlineSeconds
            policy = jobDescription.restartPolicy.policy
            it
          }
        }

        if (jobDescription.artifacts) {
          artifacts = jobDescription.artifacts.collect { artifact ->
            new Artifact().with {
              uri = artifact.uri
              cache = artifact.cache
              executable = artifact.executable
              extract = artifact.extract
              it
            }
          }
        }

        if (jobDescription.volumes) {
          volumes = jobDescription.volumes.collect { volume ->
            new LocalVolume().with {
              containerPath = volume.containerPath
              hostPath = volume.hostPath
              mode = volume.mode
              it
            }
          }
        }

        if (jobDescription.docker?.image) {
          docker = new Docker().with {
            image = "${jobDescription.docker.image.registry}/${jobDescription.docker.image.repository}:${jobDescription.docker.image.tag}".toString()
            it
          }
        }

        if (jobDescription.env) {
          env = jobDescription.env.clone()
          env.each { key, value ->
            if (!(value instanceof Map)) env[key] = value.toString()
          }
        }

        if (jobDescription.secrets) {
          secrets = jobDescription.secrets.clone()
        }

        it
      }

      if (jobDescription.schedule) {
        addSchedule(new JobSchedule().with {
          id = jobDescription.schedule.id ? jobDescription.schedule.id : 'default'
          enabled = jobDescription.schedule.enabled
          cron = jobDescription.schedule.cron
          timezone = jobDescription.schedule.timezone
          startingDeadlineSeconds = jobDescription.schedule.startingDeadlineSeconds
          concurrencyPolicy = "ALLOW"
          it
        })
      }

      it
    }
  }
}
