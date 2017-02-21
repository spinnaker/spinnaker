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

import com.netflix.spinnaker.clouddriver.appengine.AppengineJobExecutor
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineMutexRepository
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineDescription
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import org.springframework.beans.factory.annotation.Autowired

import java.nio.file.Paths

class DeployAppengineAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  AppengineJobExecutor jobExecutor

  DeployAppengineDescription description

  DeployAppengineAtomicOperation(DeployAppengineDescription description) {
    this.description = description
  }
  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["path/to/app.yaml"] } } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["path/to/app.yaml"], "promote": true, "stopPreviousVersion": true } } ]' "http://localhost:7002/appengine/ops"
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    def directoryPath = getFullDirectoryPath(description.credentials.localRepositoryDirectory, description.repositoryUrl)

    /*
    * We can't allow concurrent deploy operations on the same local repository.
    * If operation A checks out a new branch before operation B has run 'gcloud app deploy',
    * operation B will deploy using that new branch's source files.
    * */
    return AppengineMutexRepository.atomicWrapper(directoryPath, {
      task.updateStatus BASE_PHASE, "Initializing creation of version..."
      def result = new DeploymentResult()
      def newVersionName = deploy(cloneOrUpdateLocalRepository(directoryPath, 1))
      def region = description.credentials.region
      result.serverGroupNames = Arrays.asList("$region:$newVersionName".toString())
      result.serverGroupNameByRegion[region] = newVersionName
      result
    })
  }

  String cloneOrUpdateLocalRepository(String directoryPath, Integer retryCount) {
    def repositoryUrl = description.repositoryUrl
    def branch = description.branch
    def directory = new File(directoryPath)
    def repositoryClient = description.credentials.gitCredentials.buildRepositoryClient(
      repositoryUrl,
      directoryPath,
      description.gitCredentialType
    )

    try {
      if (!directory.exists()) {
        task.updateStatus BASE_PHASE, "Cloning repository $repositoryUrl into local directory..."
        directory.mkdir()
        repositoryClient.cloneRepository()
      }
      task.updateStatus BASE_PHASE, "Fetching updates from $repositoryUrl..."
      repositoryClient.fetch()
      task.updateStatus BASE_PHASE, "Checking out branch $branch..."
      repositoryClient.checkout(branch)
    } catch (Exception e) {
      directory.deleteDir()
      if (retryCount > 0) {
        return cloneOrUpdateLocalRepository(directoryPath,  retryCount - 1)
      } else {
        throw e
      }
    }
    return directoryPath
  }

  String deploy(String directoryPath) {
    def project = description.credentials.project
    def accountEmail = description.credentials.serviceAccountEmail
    def region = description.credentials.region
    def serverGroupNameResolver = new AppengineServerGroupNameResolver(project, region, description.credentials)
    def versionName = serverGroupNameResolver.resolveNextServerGroupName(description.application,
                                                                         description.stack,
                                                                         description.freeFormDetails,
                                                                         false)
    def fullyQualifiedConfigFilepaths = description.configFilepaths.collect { "$directoryPath/$it" }
    def deployCommand = ["gcloud", "app", "deploy", *fullyQualifiedConfigFilepaths]
    deployCommand << "--version=$versionName"
    deployCommand << (description.promote ? "--promote" : "--no-promote")
    deployCommand << (description.stopPreviousVersion ? "--stop-previous-version": "--no-stop-previous-version")
    deployCommand << "--project=$project"
    deployCommand << "--account=$accountEmail"

    task.updateStatus BASE_PHASE, "Deploying version $versionName..."
    jobExecutor.runCommand(deployCommand)
    return versionName
  }

  static String getFullDirectoryPath(String localRepositoryDirectory, String repositoryUrl) {
    return Paths.get(localRepositoryDirectory, repositoryUrl.replace('/', '-')).toString()
  }
}
