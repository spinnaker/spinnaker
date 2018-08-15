package com.netflix.spinnaker.clouddriver.ecs.controllers.servergroup

import com.amazonaws.services.ecs.model.ServiceEvent;
import com.netflix.spinnaker.clouddriver.ecs.model.EcsServerGroupEventStatus;
import spock.lang.Specification;

class ServerGroupEventStatusConverterSpec extends Specification {

  def judge = new ServerGroupEventStatusConverter()

  def 'should infer successful status on successful messages'() {
    given:
    def event = new ServiceEvent()
      .withMessage("service clouddriver-dev-v0013) has reached a steady state.")

    when:
    def status = judge.inferEventStatus(event)

    then:
    status == EcsServerGroupEventStatus.Success
  }

  def 'should infer failed status when no container instances are available in a cluster'() {
    given:
    def event = new ServiceEvent()
      .withMessage("(service clouddriver-dev-v0013) was unable to place a task because no container instance met all of its requirements. Reason: No Container Instances were found in your cluster. For more information, see the Troubleshooting section of the Amazon ECS Developer Guide.")

    when:
    def status = judge.inferEventStatus(event)

    then:
    status == EcsServerGroupEventStatus.Failure
  }


  def 'should infer failed status when there is insufficient capacity in the cluster'() {
    given:
    def event = new ServiceEvent()
      .withMessage("(service clouddriver-dev-v0013) was unable to place a task because no container instance met all of its requirements. The closest matching (container-instance b2db08b9-0e55-4f7e-8331-f9fb0a707938) has insufficient CPU units available. For more information, see the Troubleshooting section of the Amazon ECS Developer Guide.")

    when:
    def status = judge.inferEventStatus(event)

    then:
    status == EcsServerGroupEventStatus.Failure
  }


  def 'should infer transition status when there a task is registered on a target group'() {
    given:
    def event = new ServiceEvent()
      .withMessage("(service clouddriver-dev-v0013) registered 1 targets in (target-group arn:aws:elasticloadbalancing:us-west-2:769716316905:targetgroup/clouddriver-dev/83a346e96e7ef4d1)")

    when:
    def status = judge.inferEventStatus(event)

    then:
    status == EcsServerGroupEventStatus.Transition
  }

  def 'should infer transition status when a task is started'() {
    given:
    def event = new ServiceEvent()
      .withMessage("(service clouddriver-dev-v0013) has started 1 tasks: (task 924ca50e-f291-4d5e-af11-1523f887b976).")

    when:
    def status = judge.inferEventStatus(event)

    then:
    status == EcsServerGroupEventStatus.Transition
  }


  def 'should infer transition status when there a task is unregistered'() {
    given:
    def event = new ServiceEvent()
      .withMessage("(service clouddriver-dev-v0013) has stopped 1 running tasks: (task 924ca50e-f291-4d5e-af11-1523f887b976).")

    when:
    def status = judge.inferEventStatus(event)

    then:
    status == EcsServerGroupEventStatus.Transition
  }

  def 'should infer transition status when tasks begin draining'() {
    given:
    def event = new ServiceEvent()
      .withMessage("(service clouddriver-dev-v0013) has begun draining connections on 1 tasks.")

    when:
    def status = judge.inferEventStatus(event)

    then:
    status == EcsServerGroupEventStatus.Transition
  }
}
