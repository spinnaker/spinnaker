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
import com.netflix.spinnaker.clouddriver.appengine.artifacts.GcsStorageService
import com.netflix.spinnaker.clouddriver.appengine.artifacts.config.StorageConfigurationProperties
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineMutexRepository
import com.netflix.spinnaker.clouddriver.appengine.deploy.AppengineServerGroupNameResolver
import com.netflix.spinnaker.clouddriver.appengine.deploy.description.DeployAppengineDescription
import com.netflix.spinnaker.clouddriver.appengine.deploy.exception.AppengineOperationException
import com.netflix.spinnaker.clouddriver.appengine.gcsClient.AppengineGcsRepositoryClient
import com.netflix.spinnaker.clouddriver.artifacts.ArtifactDownloader
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.deploy.DeploymentResult
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.kork.artifacts.model.Artifact

import com.netflix.spectator.api.Registry
import groovy.util.logging.Slf4j
import java.nio.file.Path
import java.nio.file.Paths

import org.springframework.beans.factory.annotation.Autowired
import static com.netflix.spinnaker.clouddriver.appengine.config.AppengineConfigurationProperties.ManagedAccount.GcloudReleaseTrack
import java.util.concurrent.TimeUnit


class DeployAppengineAtomicOperation implements AtomicOperation<DeploymentResult> {
  private static final String BASE_PHASE = "DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  @Autowired
  Registry registry

  @Autowired
  AppengineJobExecutor jobExecutor

  @Autowired(required=false)
  StorageConfigurationProperties storageConfiguration

  @Autowired(required=false)
  GcsStorageService.Factory storageServiceFactory

  @Autowired
  ArtifactDownloader artifactDownloader

  DeployAppengineDescription description
  boolean usesGcs
  boolean containerDeployment

  DeployAppengineAtomicOperation(DeployAppengineDescription description) {
    this.description = description
    if (description.artifact) {
      switch (description.artifact.type) {
        case 'gcs/object':
          String ref = description.artifact.reference
          if (!ref) {
            throw new AppengineOperationException("Missing artifact reference for GCS deploy")
          }
          description.repositoryUrl = ref.startsWith("gs://") ? ref : "gs://${ref}"
          usesGcs = true
          break
        case 'docker/image':
          if (!description.artifact.name) {
            throw new AppengineOperationException("Missing artifact name for Flex Custom deploy")
          }
          containerDeployment = description.artifact.name
          break
        default:
          throw new AppengineOperationException("Unhandled artifact type in description")
          break
      }
    } else {
      containerDeployment = description.containerImageUrl?.trim()
      usesGcs = !this.containerDeployment && description.repositoryUrl.startsWith("gs://")
    }
  }

  /**
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["app.yaml"] } } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["app.yaml"], "promote": true, "stopPreviousVersion": true } } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "repositoryUrl": "https://github.com/organization/project.git", "branch": "feature-branch", "credentials": "my-appengine-account", "configFilepaths": ["runtime: python27\napi_version: 1\nthreadsafe: true\nmanual_scaling:\n  instances: 5\ninbound_services:\n - warmup\nhandlers:\n - url: /.*\n   script: main.app"],} } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "credentials": "my-appengine-account", "containerImageUrl": "gcr.io/my-project/my-image:my-tag", "configFiles": ["env: flex\nruntime: custom\nmanual_scaling:\n  instances: 1\nresources:\n  cpu: 1\n  memory_gb: 0.5\n  disk_size_gb: 10"] } } ]' "http://localhost:7002/appengine/ops"
   * curl -X POST -H "Content-Type: application/json" -d '[ { "createServerGroup": { "application": "myapp", "stack": "stack", "freeFormDetails": "details", "credentials": "my-appengine-credential-name", "containerImageUrl": "gcr.io/my-gcr-repo/image:tag", "configArtifacts": [{ "type": "gcs/object", "name": "gs://path/to/app.yaml", "reference": "gs://path/to/app.yaml", "artifactAccount": "my-gcs-artifact-account-name" }] } } ]' "http://localhost:7002/appengine/ops"
   */
  @Override
  DeploymentResult operate(List priorOutputs) {
    def  baseDir = description.credentials.localRepositoryDirectory

    String directoryId
    if (containerDeployment) {
      directoryId = description.containerImageUrl
    } else {
      directoryId = description.repositoryUrl
    }

    def directoryPath = getFullDirectoryPath(baseDir, directoryId)
    def serviceAccount = description.credentials.serviceAccountEmail
    def region = description.credentials.region

    /*
    * We can't allow concurrent deploy operations on the same local repository.
    * If operation A checks out a new branch before operation B has run 'gcloud app deploy',
    * operation B will deploy using that new branch's source files.
    *
    * This means if one were to deploy to multiple regions, that the deployments
    * will be serialized. If this is a problem, the mutex could be removed in
    * favor of using temporary directories encapsulated to the request. However
    * this means that the downloads would not be incremental without more work.
    */
    registry.counter(registry.createId("appengine.deployStart",
                                       "account", serviceAccount,
                                       "region", region))
            .increment()
    return AppengineMutexRepository.atomicWrapper(directoryPath, {
      task.updateStatus BASE_PHASE, "Initializing creation of version..."
      String newVersionName
      String deployPath

      if (containerDeployment) {
        createEmptyDirectory(directoryPath)
        deployPath = directoryPath
      } else {
        def startTime = registry.clock().monotonicTime()
        def success = "false"
        try {
          deployPath = cloneOrUpdateLocalRepository(directoryPath, 1)
          success = "true"
        } finally {
          def duration = registry.clock().monotonicTime() - startTime
          registry.timer(
              registry.createId("appengine.repositoryDownload",
                                "account", serviceAccount,
                                "repositoryType", usesGcs ? "gcs" : "git",
                                "success", success))
              .record(duration, TimeUnit.NANOSECONDS);
        }
      }
      def startTime = registry.clock().monotonicTime()
      def success = "false"
      try {
          newVersionName = deploy(deployPath)
          success = "true"
      } finally {
          def duration = registry.clock().monotonicTime() - startTime
          registry.timer(registry.createId("appengine.deploy",
                                           "success", success,
                                           "account", serviceAccount,
                                           "region", region))
              .record(duration, TimeUnit.NANOSECONDS);
      }

      def result = new DeploymentResult()
      result.serverGroupNames = Arrays.asList("$region:$newVersionName".toString())
      result.serverGroupNameByRegion[region] = newVersionName
      success = "true"
      return result
    })
  }

  void createEmptyDirectory(String path) {
    File directory = new File(path)
    if (directory.exists()) {
      directory.deleteDir()
    }
    if (!directory.mkdirs()) {
      throw new AppengineOperationException("Failed to create directory: $path")
    }
  }

  String cloneOrUpdateLocalRepository(String directoryPath, Integer retryCount) {
    def repositoryUrl = description.repositoryUrl
    def directory = new File(directoryPath)
    def branch = description.branch
    def branchLogName = branch
    def repositoryClient

    if (usesGcs) {
      if (storageConfiguration == null) {
        throw new IllegalStateException(
            "GCS has been disabled. To enable it, configure storage.gcs.enabled=false and restart clouddriver.")
      }

      def applicationDirectoryRoot = description.applicationDirectoryRoot
      String credentialPath = ""
      String storageAccountName = description.storageAccountName
      if (storageAccountName != null && !storageAccountName.isEmpty()) {
        credentialPath = storageConfiguration.getAccount(description.storageAccountName).jsonPath
      } else {
        storageAccountName = "ApplicationDefaultCredentials";
      }
      GcsStorageService storage = storageServiceFactory.newForCredentials(credentialPath)
      repositoryClient = new AppengineGcsRepositoryClient(repositoryUrl, directoryPath, applicationDirectoryRoot,
                                                          storage, jobExecutor)
      branchLogName = "(current)"
    } else {
      repositoryClient = description.credentials.gitCredentials.buildRepositoryClient(
        repositoryUrl,
        directoryPath,
        description.gitCredentialType
      )
    }

    try {
      if (!directory.exists()) {
        task.updateStatus BASE_PHASE, "Grabbing repository $repositoryUrl into local directory..."
        directory.mkdir()
        repositoryClient.initializeLocalDirectory()
      }
      task.updateStatus BASE_PHASE, "Fetching updates from $repositoryUrl for $branchLogName..."
      repositoryClient.updateLocalDirectoryWithVersion(branch)
    } catch (Exception e) {
      directory.deleteDir()
      if (retryCount > 0) {
        return cloneOrUpdateLocalRepository(directoryPath, retryCount - 1)
      } else {
        throw e
      }
    }
    return directoryPath
  }

  String deploy(String repositoryPath) {
    def project = description.credentials.project
    def accountEmail = description.credentials.serviceAccountEmail
    def region = description.credentials.region
    def applicationDirectoryRoot = description.applicationDirectoryRoot
    def gcloudReleaseTrack = description.credentials.gcloudReleaseTrack
    def serverGroupNameResolver = new AppengineServerGroupNameResolver(project, region, description.credentials)
    def versionName = serverGroupNameResolver.resolveNextServerGroupName(description.application,
                                                                         description.stack,
                                                                         description.freeFormDetails,
                                                                         false)
    def imageUrl = description.containerImageUrl
    def configFiles = description.configFiles
    def writtenFullConfigFilePaths = writeConfigFiles(configFiles, repositoryPath, applicationDirectoryRoot)
    def repositoryFullConfigFilePaths =
      (description.configFilepaths?.collect { Paths.get(repositoryPath, applicationDirectoryRoot ?: '.', it).toString() } ?: []) as List<String>
    def configArtifactPaths = fetchConfigArtifacts(description.configArtifacts, repositoryPath, applicationDirectoryRoot)

    def deployCommand = ["gcloud"]
    if (gcloudReleaseTrack != null && gcloudReleaseTrack != GcloudReleaseTrack.STABLE) {
      deployCommand << gcloudReleaseTrack.toString().toLowerCase()
    }
    deployCommand += ["app", "deploy", *(repositoryFullConfigFilePaths + writtenFullConfigFilePaths + configArtifactPaths)]
    deployCommand << "--version=$versionName"
    deployCommand << (description.promote ? "--promote" : "--no-promote")
    deployCommand << (description.stopPreviousVersion ? "--stop-previous-version": "--no-stop-previous-version")
    deployCommand << "--project=$project"
    deployCommand << "--account=$accountEmail"
    if (containerDeployment) {
      deployCommand << "--image-url=$imageUrl"
    }

    task.updateStatus BASE_PHASE, "Deploying version $versionName..."
    def startTime = registry.clock().monotonicTime()
    def success = "false"
    try {
      jobExecutor.runCommand(deployCommand)
      success = "true"
    } catch (e) {
      throw new AppengineOperationException("Failed to deploy to App Engine with command ${deployCommand.join(' ')}: ${e.getMessage()}")
    } finally {
      def duration = registry.clock().monotonicTime() - startTime
      def id = registry.createId("appengine.deploy",
                                 "account", description.credentials.serviceAccountEmail,
                                 "region", description.credentials.region,
                                 "success", success)
      registry.timer(id).record(duration, TimeUnit.NANOSECONDS);
      deleteFiles(writtenFullConfigFilePaths)
    }
    task.updateStatus BASE_PHASE, "Done deploying version $versionName..."
    return versionName
  }

  List<String> fetchConfigArtifacts(List<Artifact> configArtifacts, String repositoryPath, String applicationDirectoryRoot) {
    if (!configArtifacts) {
      return [];
    } else {
      return configArtifacts.collect { artifact ->
        def path = generateRandomRepositoryFilePath(repositoryPath, applicationDirectoryRoot)
        try {
          path.toFile() << artifactDownloader.download(artifact)
        } catch(e) {
          throw new AppengineOperationException("Could not download artifact as config file: ${e.getMessage()}")
        }
        return path.toString()
      }
    }
  }

  static List<String> writeConfigFiles(List<String> configFiles, String repositoryPath, String applicationDirectoryRoot) {
    if (!configFiles) {
      return []
    } else {
      return configFiles.collect { configFile ->
        def path = generateRandomRepositoryFilePath(repositoryPath, applicationDirectoryRoot)
        try {
          path.toFile() << configFile
        } catch(e) {
          throw new AppengineOperationException("Could not write config file: ${e.getMessage()}")
        }
        return path.toString()
      }
    }
  }

  static void deleteFiles(List<String> paths) {
    paths.each { path ->
      try {
        new File(path).delete()
      } catch(e) {
        throw new AppengineOperationException("Could not delete config file: ${e.getMessage()}")
      }
    }
  }

  static String getFullDirectoryPath(String localRepositoryDirectory, String repositoryUrl) {
    return Paths.get(localRepositoryDirectory, repositoryUrl.replace('/', '-')).toString()
  }

  static Path generateRandomRepositoryFilePath(String repositoryPath, String applicationDirectoryRoot) {
    def name = UUID.randomUUID().toString()
    return Paths.get(repositoryPath, applicationDirectoryRoot ?: ".", "${name}.yaml")
  }
}
