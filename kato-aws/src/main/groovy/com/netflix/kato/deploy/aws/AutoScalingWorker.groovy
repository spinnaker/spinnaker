package com.netflix.kato.deploy.aws

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.netflix.frigga.Names
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import com.netflix.kato.deploy.aws.userdata.UserDataProvider
import groovy.transform.InheritConstructors
import org.apache.commons.codec.binary.Base64
import org.joda.time.LocalDateTime
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.client.RestTemplate

class AutoScalingWorker {
  private static final String AWS_PHASE = "AWS_DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private String application
  private String region
  private String environment
  private String stack
  private String ami
  private String instanceType
  private List<String> loadBalancers
  private List<String> securityGroups
  private List<String> availabilityZones
  private AmazonEC2 amazonEC2
  private AmazonAutoScaling autoScaling
  private RestTemplate rt = new RestTemplate()

  private int minInstances
  private int maxInstances
  private int desiredInstances

  private List<UserDataProvider> userDataProviders = []

  AutoScalingWorker() {

  }

  String deploy() {
    task.updateStatus AWS_PHASE, "Beginning Amazon deployment."

    task.updateStatus AWS_PHASE, "Checking for security package."
    String packageSecurityGroup
    try {
      packageSecurityGroup = getSecurityGroupForApplication()
    } catch (SecurityGroupNotFoundException IGNORE) {
      task.updateStatus AWS_PHASE, "Not found, creating."
      packageSecurityGroup = createSecurityGroup()
    }

    task.updateStatus AWS_PHASE, "Looking up security groups..."
    if (securityGroups) {
      securityGroups = getSecurityGroupIds(securityGroups as String[])
    } else {
      securityGroups = getSecurityGroupIds(["nf-datacenter", "nf-infrastructure"] as String[])
    }

    task.updateStatus AWS_PHASE,"Beginning ASG deployment."
    securityGroups << packageSecurityGroup
    Map ancestorAsg = ancestorAsg
    Integer nextSequence
    if (ancestorAsg) {
      task.updateStatus AWS_PHASE, "Found ancestor ASG: parsing details."
      Names ancestorNames = Names.parseName(ancestorAsg.autoScalingGroupName as String)
      nextSequence = ancestorNames.sequence + 1
      task.updateStatus AWS_PHASE, "Copying security groups from ancestor ASG."
      List<String> ancestorSecurityGroups = getSecurityGroupsForLaunchConfiguration(ancestorAsg.launchConfigurationName as String)
      if (!ancestorSecurityGroups.contains(packageSecurityGroup)) {
        ancestorSecurityGroups << packageSecurityGroup
      }
      securityGroups.addAll ancestorSecurityGroups
    } else {
      nextSequence = 0
    }

    String asgName = getAutoScalingGroupName(nextSequence)
    String launchConfigName = getLaunchConfigurationName(nextSequence)

    def userData = getUserData(asgName, launchConfigName)
    task.updateStatus AWS_PHASE, "Building launch configuration for new ASG."
    createLaunchConfiguration(launchConfigName, userData, securityGroups?.unique())
    task.updateStatus AWS_PHASE, "Deploying ASG."

    createAutoScalingGroup(asgName, launchConfigName)
  }

  String getLaunchConfigurationName(Integer nextSequence) {
    def nowTime = new LocalDateTime().toString("MMddYYYYHHmmss")
    "${getAutoScalingGroupName(nextSequence)}-${nowTime}"
  }

  Map getAncestorAsg() {
    List<String> asgs = rt.getForEntity("http://entrypoints-v2.${region}.${environment}.netflix.net:7001/REST/v2/aws/autoScalingGroups", List).body
    def lastAsgName = asgs.findAll {
      def names = Names.parseName(it)
      names.sequence >= 0 && application == names.app
    }?.max()

    if (lastAsgName) {
      rt.getForEntity("http://entrypoints-v2.${region}.${environment}.netflix.net:7001/REST/v2/aws/autoScalingGroups/$lastAsgName", Map).body
    } else {
      null
    }
  }

  List<String> getSecurityGroupsForLaunchConfiguration(String launchConfigName) {
    Map launchConfiguration = rt.getForEntity("http://entrypoints-v2.${region}.${environment}.netflix.net:7001/REST/v2/aws/launchConfigurations/$launchConfigName", Map).body
    launchConfiguration.securityGroups
  }

  String getSecurityGroupForApplication() {
    getSecurityGroupIds([application] as String[])?.getAt(0)
  }

  List<String> getSecurityGroupIds(String...names) {
    DescribeSecurityGroupsResult result = amazonEC2.describeSecurityGroups()
    def securityGroups = result.securityGroups.findAll { names.contains(it.groupName) }.collectEntries {
      [(it.groupName): it.groupId]
    }
    if (names.minus(securityGroups.keySet()).size() > 0) {
      throw new SecurityGroupNotFoundException()
    }
    securityGroups.keySet() as List
  }

  @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "could not find all supplied security groups!")
  @InheritConstructors
  static class SecurityGroupNotFoundException extends RuntimeException {}

  String createSecurityGroup() {
    CreateSecurityGroupRequest request = new CreateSecurityGroupRequest(application, "Security Group for $application")
    CreateSecurityGroupResult result = amazonEC2.createSecurityGroup(request)
    result.groupId
  }

  String createLaunchConfiguration(String name, String userData, List<String> securityGroups) {
    CreateLaunchConfigurationRequest request = new CreateLaunchConfigurationRequest()
        .withImageId(ami)
        .withIamInstanceProfile("BaseIAMRole")
        .withInstanceMonitoring(new com.amazonaws.services.autoscaling.model.InstanceMonitoring().withEnabled(true))
        .withLaunchConfigurationName(name)
        .withUserData(userData)
        .withInstanceType(instanceType)
        .withSecurityGroups(securityGroups)
        .withKeyName("nf-test-keypair-a")
    autoScaling.createLaunchConfiguration(request)

    name
  }

  String getAutoScalingGroupName(Integer sequence) {
    def pushVersion = String.format("v%03d", sequence)
    "${application}-${stack?.replaceAll("$application-", "")}-${pushVersion}"
  }

  String createAutoScalingGroup(String asgName, String launchConfigurationName) {
    CreateAutoScalingGroupRequest request = new CreateAutoScalingGroupRequest()
        .withAutoScalingGroupName(asgName)
        .withLaunchConfigurationName(launchConfigurationName)
        .withMinSize(minInstances)
        .withMaxSize(maxInstances)
        .withDesiredCapacity(desiredInstances)
        .withDefaultCooldown(10)
        .withHealthCheckGracePeriod(600)
        .withAvailabilityZones(availabilityZones)
        .withLoadBalancerNames(loadBalancers)
    autoScaling.createAutoScalingGroup(request)

    asgName
  }

  String getUserData(String asgName, String launchConfigName) {
    def data = userDataProviders.collect { udp ->
      udp.getUserData(asgName, launchConfigName, region, environment)
    }?.join("\n")
    data ? new String(Base64.encodeBase64(data?.bytes)) : null
  }

}
