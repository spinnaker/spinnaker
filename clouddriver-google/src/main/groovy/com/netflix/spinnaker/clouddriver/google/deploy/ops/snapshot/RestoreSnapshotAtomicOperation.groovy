/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.google.deploy.ops.snapshot

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.clouddriver.core.services.Front50Service
import com.netflix.spinnaker.clouddriver.data.task.Task
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.google.config.GoogleConfigurationProperties
import com.netflix.spinnaker.clouddriver.google.deploy.description.snapshot.RestoreSnapshotDescription
import com.netflix.spinnaker.clouddriver.google.model.GoogleCluster
import com.netflix.spinnaker.clouddriver.google.model.GoogleSecurityGroup
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerView
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleNetworkLoadBalancer
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleClusterProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleLoadBalancerProvider
import com.netflix.spinnaker.clouddriver.google.provider.view.GoogleSecurityGroupProvider
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import com.netflix.spinnaker.clouddriver.jobs.JobExecutor
import com.netflix.spinnaker.clouddriver.jobs.JobRequest
import com.netflix.spinnaker.clouddriver.jobs.JobStatus
import com.netflix.spinnaker.clouddriver.orchestration.AtomicOperation
import com.netflix.spinnaker.clouddriver.security.AccountCredentialsRepository
import org.springframework.beans.factory.annotation.Autowired

class RestoreSnapshotAtomicOperation implements AtomicOperation<Void> {
  private static final String BASE_PHASE = "RESTORE_SNAPSHOT"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private final RestoreSnapshotDescription description
  private final String applicationName
  private final String accountName
  private final Long snapshotTimestamp
  private final String directory
  private String project
  private String credentialPath
  private Set imported
  private List applicationTags
  private boolean hasEnvCredentials

  @Autowired GoogleConfigurationProperties googleConfigurationProperties

  @Autowired
  GoogleClusterProvider googleClusterProvider

  @Autowired
  GoogleLoadBalancerProvider googleLoadBalancerProvider

  @Autowired
  GoogleSecurityGroupProvider googleSecurityGroupProvider

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository

  @Autowired
  JobExecutor jobExecutor

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  Front50Service front50Service

  RestoreSnapshotAtomicOperation(RestoreSnapshotDescription description) {
    this.description = description
    this.applicationName = description.applicationName
    this.accountName = description.accountName
    this.snapshotTimestamp = description.snapshotTimestamp
    this.directory = "$applicationName-$accountName"
    this.imported = new HashSet()
    hasEnvCredentials = false
    this.applicationTags = []
  }

  /* curl -X POST -H "Content-Type: application/json" -d '[ { "restoreSnapshot": { "applicationName": "example", "credentials": "my-google-account" "snapshotTimestamp": "123456789"}} ]' localhost:7002/gce/ops */
  @Override
  Void operate(List priorOutputs) {
    // Set up google credentials
    def credentials = accountCredentialsRepository.getOne(accountName) as GoogleNamedAccountCredentials
    project = credentials.project
    def pb = new ProcessBuilder()
    def env = pb.environment()
    if (env.GOOGLE_PROJECT == project && env.GOOGLE_CREDENTIALS && env.GOOGLE_REGION) {
      hasEnvCredentials = true
    } else {
      for (GoogleConfigurationProperties.ManagedAccount account : googleConfigurationProperties.accounts) {
        if (account.name == accountName) {
          credentialPath = account.jsonPath
          break
        }
      }
    }
    if (credentialPath == null && !hasEnvCredentials) {
      throw new IllegalStateException("Could not find credentials for $accountName")
    }

    // Make directory for terraform files
    File dir = new File(directory);
    if (!dir.mkdir()) {
      throw new IllegalStateException("Error creating directory $directory")
    }

    task.updateStatus BASE_PHASE, "Importing state of server groups for the application ${applicationName} in account ${accountName}"
    googleClusterProvider.getClusters(applicationName, accountName).each { GoogleCluster.View cluster ->
      cluster.serverGroups.each { GoogleServerGroup.View serverGroup ->
        importServerGroupState(serverGroup)
      }
    }

    task.updateStatus BASE_PHASE, "Importing state of load balancers for the application ${applicationName} in account ${accountName}"
    googleLoadBalancerProvider.getApplicationLoadBalancers(applicationName).each { GoogleLoadBalancerView loadBalancer ->
      if (loadBalancer.account == accountName) {
        importLoadBalancerState(loadBalancer)
      }
    }

    task.updateStatus BASE_PHASE, "Importing state of security groups for application ${applicationName} in account ${accountName}"
    googleSecurityGroupProvider.getAll(true).each { GoogleSecurityGroup securityGroup ->
      if (securityGroup.accountName == accountName && securityGroup.targetTags && !Collections.disjoint(securityGroup.targetTags, applicationTags)) {
        importSecurityGroupState(securityGroup)
      }
    }

    task.updateStatus BASE_PHASE, "Restoring snapshot with timestamp ${snapshotTimestamp} for application ${applicationName} in account ${accountName}"
    createTerraformConfig()
    ArrayList<String> command = ["terraform", "apply", "-state=$directory/terraform.tfstate", "$directory"]
    JobStatus jobStatus = jobExecutor.runJob(new JobRequest(command), System.getenv(), new ByteArrayInputStream())
    cleanUpDirectory()
    if (jobStatus.getResult() == JobStatus.Result.FAILURE && jobStatus.getStdOut()) {
      String stdOut = jobStatus.getStdOut()
      String stdErr = jobStatus.getStdErr()
      throw new IllegalArgumentException("$stdOut + $stdErr")
    }
    return null
  }

  private Void importServerGroupState(GoogleServerGroup.View serverGroup) {
    importResource("google_compute_instance_group_manager", serverGroup.name, serverGroup.name, serverGroup.region)
    def instanceTemplate = serverGroup.imageSummary.image
    if (instanceTemplate && !imported.contains(instanceTemplate.name)) {
      importResource("google_compute_instance_template", instanceTemplate.name, instanceTemplate.name, serverGroup.region)
      imported.add(instanceTemplate.name)
      if (instanceTemplate.properties.tags?.items) {
        applicationTags.addAll(instanceTemplate.properties.tags.items)
      }
    }
    if (serverGroup.autoscalingPolicy) {
      importResource("google_compute_autoscaler", serverGroup.name, serverGroup.name, serverGroup.region)
    }
    // Add dependencies of existing resources to the state file
    def stateFile = new File("$directory/terraform.tfstate")
    String stateJson = new Scanner(stateFile).useDelimiter("\\Z").next()
    Map state = objectMapper.readValue(stateJson, new TypeReference<HashMap<String,Object>>() {})
    def igmDependencies = serverGroup.loadBalancers.collect { String loadBalancer ->
      return "google_compute_target_pool.$loadBalancer"
    }
    igmDependencies << "google_compute_instance_template.$instanceTemplate.name"
    addDependencies(state.modules[0].resources["google_compute_instance_group_manager.$serverGroup.name"], igmDependencies)
    if (serverGroup.autoscalingPolicy) {
      addDependencies(state.modules[0].resources["google_compute_autoscaler.$serverGroup.name"], ["google_compute_instance_group_manager.$serverGroup.name"])
    }
    stateFile.newWriter().withWriter { w ->
      w << objectMapper.writeValueAsString(state)
    }
    return null
  }

  private Void importLoadBalancerState(GoogleLoadBalancerView loadBalancer) {
    if (loadBalancer instanceof GoogleNetworkLoadBalancer.View) {
      def targetPoolName = loadBalancer.targetPool.split("/").last()
      importResource("google_compute_target_pool", loadBalancer.name, targetPoolName, loadBalancer.region)
      importResource("google_compute_forwarding_rule", loadBalancer.name, loadBalancer.name, loadBalancer.region)
      if (loadBalancer.healthCheck && !imported.contains(loadBalancer.healthCheck.name)) {
        importResource("google_compute_http_health_check", loadBalancer.healthCheck.name, loadBalancer.healthCheck.name, loadBalancer.region)
        imported.add(loadBalancer.healthCheck.name)
      }
      // Add dependencies of existing resources to the state file
      def stateFile = new File("$directory/terraform.tfstate")
      String stateJson = new Scanner(stateFile).useDelimiter("\\Z").next()
      Map state = objectMapper.readValue(stateJson, new TypeReference<HashMap<String, Object>>() {})
      addDependencies(state.modules[0].resources["google_compute_forwarding_rule.$loadBalancer.name"], ["google_compute_target_pool.$loadBalancer.name"])
      if (loadBalancer.healthCheck) {
        addDependencies(state.modules[0].resources["google_compute_target_pool.$loadBalancer.name"], ["google_compute_http_health_check.$loadBalancer.healthCheck.name"])
      }
      stateFile.newWriter().withWriter { w ->
        w << objectMapper.writeValueAsString(state)
      }
    }
    return null
  }

  private Void importSecurityGroupState(GoogleSecurityGroup securityGroup) {
    importResource("google_compute_firewall", securityGroup.name, securityGroup.name, securityGroup.region)
    return null
  }

  private void importResource(String resource, String name, String id, String region) {
    InputStream inputStream
    def pb = new ProcessBuilder()
    Map env = pb.environment()
    if (!hasEnvCredentials) {
      inputStream = new ByteArrayInputStream("$credentialPath\n$project\n$region".toString().getBytes())
    } else {
      inputStream = new ByteArrayInputStream()
      env.GOOGLE_REGION = region
    }
    ArrayList<String> command = ["terraform", "import", "-state=$directory/terraform.tfstate", "$resource.$name", id]
    JobStatus jobStatus = jobExecutor.runJob(new JobRequest(command), env, inputStream)
    if (jobStatus.getResult() == JobStatus.Result.FAILURE && jobStatus.stdOut) {
      cleanUpDirectory()
      throw new IllegalArgumentException("$jobStatus.stdOut + $jobStatus.stdErr")
    }
  }

  private void createTerraformConfig() {
    Map snapshot = front50Service.getSnapshotVersion(directory, snapshotTimestamp.toString())
    Map terraformConfig = [:]
    terraformConfig.resource = snapshot.infrastructure
    if (!hasEnvCredentials) {
      Map provider = [:]
      provider.google = [:]
      provider.google.credentials = "\${file(\"$credentialPath\")}".toString()
      provider.google.project = project
      // Default region must be provided but should never be used because the individual resources will specify their region
      provider.google.region = "us-central1"
      terraformConfig.provider = provider
    }
    def file = new File("$directory/snapshot.tf.json")
    file.write(objectMapper.writeValueAsString(terraformConfig))
  }

  private void addDependencies(Map resource, List dependencies) {
    if (resource.depends_on == null) {
      resource.depends_on = []
    }
    for (String dependency: dependencies) {
      resource.depends_on << dependency
    }
  }

  private void cleanUpDirectory() {
    List<File> files = new ArrayList<>()
    files << new File("$directory/terraform.tfstate")
    files << new File("$directory/terraform.tfstate.backup")
    files << new File("$directory/snapshot.tf.json")
    for (File file: files) {
      if (file.exists()) {
        if (!file.delete()) {
          throw new IllegalStateException("Error deleting file $file.name")
        }
      }
    }
    def dir = new File("$directory")
    if (!dir.delete()) {
      throw new IllegalStateException("Error deleting directory $dir.name")
    }
  }
}
