/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.deploy.ops

import com.netflix.spinnaker.clouddriver.appengine.deploy.AppEngineMutexRepository
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppEngineServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppEngineDescription
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor
import com.netflix.spinnaker.clouddriver.jobs.JobRequest
import com.netflix.spinnaker.clouddriver.jobs.JobStatus
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

class DeployAppEngineAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  JobExecutor jobExecutor

  DeployAppEngineDescription description

  DeployAppEngineAtomicOperation(DeployAppEngineDescription description) {
    this.description = description
  }
  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "appYamlPath": "path/to/app.yaml" } } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "appYamlPath": "path/to/app.yaml", "promote": true, "stopPreviousVersion": true } } ]' "http://localhost:7002/appengine/ops"
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    def directoryName = getLegalDirectoryName(description.repositoryUrl)

    /*
    * We can't allow concurrent deploy operations on the same local repository.
    * If operation A checks out a new branch before operation B has run 'gcloud app deploy',
    * operation B will deploy using that new branch's source files.
    * */
    AppEngineMutexRepository.atomicWrapper(directoryName, {
      task.updateStatus BASE_PHASE, "Initializing creation of version..."
      def result = new DeploymentResult()
      def newVersionName = deploy(cloneOrUpdateLocalRepository(directoryName, 1))
      def region = description.credentials.region;
      result.serverGroupNames = Arrays.asList("$region:$newVersionName".toString())
      result.serverGroupNameByRegion[region] = newVersionName;
      result
    })
  }

  String cloneOrUpdateLocalRepository(String directoryName, Integer retryCount) {
    def repositoryUrl = description.repositoryUrl
    def branch = description.branch
    def directory = new File(directoryName)

    try {
      if (!directory.exists()) {
        task.updateStatus BASE_PHASE, "Cloning repository $repositoryUrl into local directory..."
        directory.mkdir()
        runCommand(["git", "clone", repositoryUrl, directoryName])
      }

      task.updateStatus BASE_PHASE, "Checking out branch $branch and merging..."
      runCommand(["git", "-C", directoryName, "fetch", "origin", branch])
      runCommand(["git", "-C", directoryName, "checkout", "origin/$branch"])
    } catch (Exception e) {
      directory.deleteDir()
      if (retryCount > 0) {
        return cloneOrUpdateLocalRepository(directoryName, retryCount - 1)
      } else {
        throw e
      }
    }
    directoryName
  }

  String deploy(String directoryName) {
    def jsonPath = description.credentials.jsonPath
    def project = description.credentials.project
    def region = description.credentials.region
    def serverGroupNameResolver = new AppEngineServerGroupNameResolver(project, region, description.credentials)
    def versionName = serverGroupNameResolver.resolveNextServerGroupName(description.application,
                                                                         description.stack,
                                                                         description.freeFormDetails,
                                                                         false)

    task.updateStatus BASE_PHASE, "Activating service account..."
    runCommand(["gcloud", "auth", "activate-service-account", "--key-file", jsonPath])

    def deployCommand = ["gcloud", "app", "deploy", "$directoryName/$description.appYamlPath"]
    deployCommand << "--version=$versionName"
    deployCommand << (description.promote ? "--promote" : "--no-promote")
    deployCommand << (description.stopPreviousVersion ? "--stop-previous-version": "--no-stop-previous-version")
    deployCommand << "--project=$project"

    task.updateStatus BASE_PHASE, "Deploying version $versionName..."
    runCommand(deployCommand)
    versionName
  }

  void runCommand(List<String> command) {
    String jobId = jobExecutor.startJob(new JobRequest(tokenizedCommand: command),
                                        System.getenv(),
                                        new ByteArrayInputStream())
    waitForJobCompletion(jobId)
  }

  void waitForJobCompletion(String jobId) {
    sleep(1000)
    JobStatus jobStatus = jobExecutor.updateJob(jobId)
    while (jobStatus.state == JobStatus.State.RUNNING) {
      sleep(1000)
      jobStatus = jobExecutor.updateJob(jobId)
    }
    if (jobStatus.result == JobStatus.Result.FAILURE && jobStatus.stdOut) {
      throw new IllegalArgumentException("$jobStatus.stdOut + $jobStatus.stdErr")
    }
  }

  static String getLegalDirectoryName(String repositoryUrl) {
    repositoryUrl.replace('/', '-')
  }
}
