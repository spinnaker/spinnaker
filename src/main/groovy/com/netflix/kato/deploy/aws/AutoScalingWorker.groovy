package com.netflix.kato.deploy.aws

import com.amazonaws.services.autoscaling.AmazonAutoScaling
import com.amazonaws.services.autoscaling.model.CreateAutoScalingGroupRequest
import com.amazonaws.services.autoscaling.model.CreateLaunchConfigurationRequest
import com.amazonaws.services.ec2.AmazonEC2
import com.amazonaws.services.ec2.model.*
import com.netflix.frigga.Names
import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import org.joda.time.LocalDateTime
import org.springframework.web.client.RestTemplate

class AutoScalingWorker {
  private static final String AWS_PHASE = "AWS_DEPLOY"

  private static Task getTask() {
    TaskRepository.threadLocalTask.get()
  }

  private String application
  private String region
  private String environment
  private String clusterName
  private String ami
  private String instanceType
  private List<String> avaabilityZones
  private AmazonEC2 amazonEC2
  private AmazonAutoScaling autoScaling
  private RestTemplate rt = new RestTemplate()

  private int minInstances
  private int maxInstances
  private int desiredInstances

  AutoScalingWorker() {

  }

  AutoScalingWorker(String application, String region, String environment, String clusterName, String ami,
                    int minInstances, int maxInstances, int desiredInstances, String instanceType,
                    List<String> availabilityZones, AmazonEC2 amazonEC2, AmazonAutoScaling autoScaling) {
    this.application = application
    this.ami = ami
    this.region = region
    this.environment = environment
    this.clusterName = clusterName
    this.minInstances = minInstances
    this.maxInstances = maxInstances
    this.desiredInstances = desiredInstances
    this.instanceType = instanceType
    this.avaabilityZones = availabilityZones
    this.amazonEC2 = amazonEC2
    this.autoScaling = autoScaling
  }

  String deploy() {
    task.updateStatus AWS_PHASE, "Beginning Amazon deployment."
    task.updateStatus AWS_PHASE, "Checking for security package."
    String packageSecurityGroup = getSecurityGroupForApplication()
    if (!packageSecurityGroup) {
      task.updateStatus AWS_PHASE, "Not found, creating."
      packageSecurityGroup = createSecurityGroup()
    }

    task.updateStatus AWS_PHASE,"Beginning ASG deployment."
    Map ancestorAsg = ancestorAsg
    Integer nextSequence
    String launchConfigurationName
    if (ancestorAsg) {
      task.updateStatus AWS_PHASE, "Found ancestor ASG: parsing details."
      Names ancestorNames = Names.parseName(ancestorAsg.autoScalingGroupName as String)
      nextSequence = ancestorNames.sequence + 1
      task.updateStatus AWS_PHASE, "Copying security groups from ancestor ASG."
      List<String> ancestorSecurityGroups = getSecurityGroupsForLaunchConfiguration(ancestorAsg.launchConfigurationName as String)
      if (!ancestorSecurityGroups.contains(packageSecurityGroup)) {
        ancestorSecurityGroups << packageSecurityGroup
      }
      task.updateStatus AWS_PHASE, "Building launch configuration for new ASG."
      launchConfigurationName = createLaunchConfiguration(null, ancestorSecurityGroups, nextSequence)
      task.updateStatus AWS_PHASE, "Deploying ASG."
    } else {
      nextSequence = 0
      task.updateStatus AWS_PHASE, "Building launch configuration for new ASG."
      launchConfigurationName = createLaunchConfiguration(null, [packageSecurityGroup], nextSequence)
      task.updateStatus AWS_PHASE, "Deploying ASG."
    }
    createAutoScalingGroup(nextSequence, launchConfigurationName)
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
    DescribeSecurityGroupsRequest request = new DescribeSecurityGroupsRequest().withGroupNames(application)
    try {
      DescribeSecurityGroupsResult result = amazonEC2.describeSecurityGroups(request)
      return result.securityGroups ? result.securityGroups.first().groupId : null
    } catch (IGNORE) {
      return null
    }
  }

  String createSecurityGroup() {
    CreateSecurityGroupRequest request = new CreateSecurityGroupRequest(application, "Security Group for $application")
    CreateSecurityGroupResult result = amazonEC2.createSecurityGroup(request)
    result.groupId
  }

  String createLaunchConfiguration(String userData, List<String> securityGroups, Integer sequence) {
    def nowTime = new LocalDateTime().toString("MMddYYYYHHmmss")
    String launchConfigName = "${getAutoScalingGroupName(sequence)}-${nowTime}"

    CreateLaunchConfigurationRequest request = new CreateLaunchConfigurationRequest()
        .withImageId(ami)
        .withIamInstanceProfile("BaseIAMRole")
        .withInstanceMonitoring(new com.amazonaws.services.autoscaling.model.InstanceMonitoring().withEnabled(true))
        .withLaunchConfigurationName(launchConfigName)
        .withUserData(userData)
        .withInstanceType(instanceType)
        .withSecurityGroups(securityGroups)
    autoScaling.createLaunchConfiguration(request)

    launchConfigName
  }


  private String getAutoScalingGroupName(Integer sequence) {
    def pushVersion = String.format("v%03d", sequence)
    "${application}-${clusterName.replaceAll("$application-", "")}-${pushVersion}"
  }

  String createAutoScalingGroup(Integer sequence, String launchConfigurationName) {
    String autoScalingGroupName = getAutoScalingGroupName(sequence)

    CreateAutoScalingGroupRequest request = new CreateAutoScalingGroupRequest()
        .withAutoScalingGroupName(autoScalingGroupName)
        .withLaunchConfigurationName(launchConfigurationName)
        .withMinSize(minInstances)
        .withMaxSize(maxInstances)
        .withDesiredCapacity(desiredInstances)
        .withDefaultCooldown(10)
        .withHealthCheckGracePeriod(600)
        .withAvailabilityZones(avaabilityZones)
    autoScaling.createAutoScalingGroup(request)

    autoScalingGroupName
  }

}
