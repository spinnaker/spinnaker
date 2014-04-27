package com.netflix.kato.deploy.aws

import com.netflix.kato.data.task.Task
import com.netflix.kato.data.task.TaskRepository
import spock.lang.Specification

class AutoScalingWorkerUnitSpec extends Specification {

  def setupSpec() {
    TaskRepository.threadLocalTask.set(Mock(Task))
  }

  void "deploy workflow is create security group, create launch config, create asg"() {
    setup:
      def mockAutoScalingWorker = Spy(AutoScalingWorker)

    when:
      mockAutoScalingWorker.deploy()

    then:
      1 * mockAutoScalingWorker.getSecurityGroupForApplication() >> "sg-1234"
      1 * mockAutoScalingWorker.getAncestorAsg() >> null
      1 * mockAutoScalingWorker.createLaunchConfiguration(null, ['sg-1234'], 0) >> { "launchConfigName" }
      1 * mockAutoScalingWorker.createAutoScalingGroup(0, "launchConfigName") >> {}
  }

  void "deploy favors security groups of ancestor asg"() {
    setup:
      def mockAutoScalingWorker = Spy(AutoScalingWorker)

    when:
      mockAutoScalingWorker.deploy()

    then:
      1 * mockAutoScalingWorker.getSecurityGroupForApplication() >> "sg-1234"
      1 * mockAutoScalingWorker.getAncestorAsg() >> {
        [autoScalingGroupName: "asgard-test-v000", launchConfigurationName: "asgard-test-v000-launchConfigName"]
      }
      1 * mockAutoScalingWorker.getSecurityGroupsForLaunchConfiguration("asgard-test-v000-launchConfigName") >> {
        ['sg-5678']
      }
      1 * mockAutoScalingWorker.createLaunchConfiguration(null, ['sg-5678', 'sg-1234'], 1) >> {
        'launchConfigName'
      }
      1 * mockAutoScalingWorker.createAutoScalingGroup(1, "launchConfigName") >> {}
  }

  void "security group is created for app if one is not found"() {
    setup:
      def mockAutoScalingWorker = Spy(AutoScalingWorker)

    when:
      mockAutoScalingWorker.deploy()

    then:
      1 * mockAutoScalingWorker.getSecurityGroupForApplication() >> null
      1 * mockAutoScalingWorker.createSecurityGroup() >> { "sg-1234" }
      1 * mockAutoScalingWorker.getAncestorAsg() >> null
      1 * mockAutoScalingWorker.createLaunchConfiguration(null, ['sg-1234'], 0) >> { "launchConfigName" }
      1 * mockAutoScalingWorker.createAutoScalingGroup(0, "launchConfigName") >> {}
  }
}
