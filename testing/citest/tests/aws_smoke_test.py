# Copyright 2016 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""
Smoke test to see if Spinnaker can interoperate with Amazon Web Services.

See testable_service/integration_test.py and spinnaker_testing/spinnaker.py
for more details.

The smoke test will use ssh to peek at the spinnaker configuration
to determine the managed project it should verify, and to determine
the spinnaker account name to use when sending it commands.

Sample Usage:
    Assuming you have created $PASSPHRASE_FILE (which you should chmod 400)
    and $CITEST_ROOT points to the root directory of this repository
    (which is . if you execute this from the root)

  PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
    python $CITEST_ROOT/spinnaker/spinnaker_system/smoke_test.py \
    --gce_ssh_passphrase_file=$PASSPHRASE_FILE \
    --gce_project=$PROJECT \
    --gce_zone=$ZONE \
    --gce_instance=$INSTANCE
    --test_aws_zone=$AWS_ZONE \
    --aws_profile=$AWS_PROFILE
or
  PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
    python $CITEST_ROOT/spinnaker/spinnaker_system/smoke_test.py \
    --native_hostname=host-running-smoke-test
    --test_aws_zone=$AWS_ZONE \
    --aws_profile=$AWS_PROFILE

  Note that the $AWS_ZONE is not directly used, rather it is a standard
  parameter being used to infer the region. The test is going to pick
  some different availability zones within the region in order to test kato.
  These are currently hardcoded in.
"""

# Standard python modules.
import sys

# citest modules.
import citest.aws_testing as aws
import citest.json_contract as jc
import citest.json_predicate as jp
import citest.service_testing as st
import citest.base

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate


class AwsSmokeTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the smoke test.

  The scenario remembers:
     * The agent used to talk to gate.
     * The name of the unique Spinnaker application we create for this test.
     * The name of the load balancer we create used.
  """

  @classmethod
  def new_agent(cls, bindings):
    """Implements citest.service_testing.AgentTestScenario.new_agent."""
    return gate.new_agent(bindings)

  def __init__(self, bindings, agent=None):
    """Constructor.

    Args:
      bindings: [dict] The data bindings to use to configure the scenario.
      agent: [GateAgent] The agent for invoking the test operations on Gate.
    """
    super(AwsSmokeTestScenario, self).__init__(bindings, agent)
    bindings = self.bindings

    self.lb_detail = 'lb'
    self.lb_name = '{app}-{stack}-{detail}'.format(
        app=bindings['TEST_APP'], stack=bindings['TEST_STACK'],
        detail=self.lb_detail)

    # We'll call out the app name because it is widely used
    # because it scopes the context of our activities.
    # pylint: disable=invalid-name
    self.TEST_APP = bindings['TEST_APP']

  def create_app(self):
    """Creates OperationContract that creates a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_create_app_operation(
            bindings=self.bindings, application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_AWS_ACCOUNT']),
        contract=contract)

  def delete_app(self):
    """Creates OperationContract that deletes a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_delete_app_operation(
             application=self.TEST_APP,
             account_name=self.bindings['SPINNAKER_AWS_ACCOUNT']),
        contract=contract)

  def upsert_load_balancer(self, use_vpc):
    """Creates OperationContract for upsertLoadBalancer.

    Calls Spinnaker's upsertLoadBalancer with a configuration, then verifies
    that the expected resources and configurations are visible on AWS. See
    the contract builder for more info on what the expectations are.

    Args:
      use_vpc: [bool] if True configure a VPC otherwise dont.
    """
    bindings = self.bindings
    context = citest.base.ExecutionContext()

    # We're assuming that the given region has 'A' and 'B' availability
    # zones. This seems conservative but might be brittle since we permit
    # any region.
    region = bindings['TEST_AWS_REGION']
    avail_zones = [region + 'a', region + 'b']
    load_balancer_name = self.lb_name

    if use_vpc:
      # TODO(ewiseblatt): 20160301
      # We're hardcoding the VPC here, but not sure which we really want.
      # I think this comes from the spinnaker.io installation instructions.
      # What's interesting about this is that it is a 10.* CidrBlock,
      # as opposed to the others, which are public IPs. All this is sensitive
      # as to where the TEST_AWS_VPC_ID came from so this is going to be
      # brittle. Ideally we only need to know the vpc_id and can figure the
      # rest out based on what we have available.
      subnet_type = 'internal (defaultvpc)'
      vpc_id = bindings['TEST_AWS_VPC_ID']

      # Not really sure how to determine this value in general.
      security_groups = ['default']

      # The resulting load balancer will only be available in the zone of
      # the subnet we are using. We'll figure that out by looking up the
      # subnet we want.
      subnet_details = self.aws_observer.get_resource_list(
          context,
          root_key='Subnets',
          aws_command='describe-subnets',
          aws_module='ec2',
          args=['--filters',
                'Name=vpc-id,Values={vpc_id}'
                ',Name=tag:Name,Values=defaultvpc.internal.{region}'
                .format(vpc_id=vpc_id, region=region)])
      try:
        expect_avail_zones = [subnet_details[0]['AvailabilityZone']]
      except KeyError:
        raise ValueError('vpc_id={0} appears to be unknown'.format(vpc_id))
    else:
      subnet_type = ""
      vpc_id = None
      security_groups = None
      expect_avail_zones = avail_zones

      # This will be a second load balancer not used in other tests.
      # Decorate the name so as not to confuse it.
      load_balancer_name += '-pub'


    listener = {
        'Listener': {
            'InstancePort':80,
            'LoadBalancerPort':80
        }
    }
    health_check = {
        'HealthyThreshold': 8,
        'UnhealthyThreshold': 3,
        'Interval': 12,
        'Timeout': 6,
        'Target':'HTTP:%d/' % listener['Listener']['InstancePort']
    }

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'type': 'upsertLoadBalancer',
            'cloudProvider': 'aws',
            # 'loadBalancerName': load_balancer_name,


            'credentials': bindings['SPINNAKER_AWS_ACCOUNT'],
            'name': load_balancer_name,
            'stack': bindings['TEST_STACK'],
            'detail': self.lb_detail,
            'region': bindings['TEST_AWS_REGION'],

            'availabilityZones': {region: avail_zones},
            'regionZones': avail_zones,
            'listeners': [{
                'internalProtocol': 'HTTP',
                'internalPort': listener['Listener']['InstancePort'],
                'externalProtocol': 'HTTP',
                'externalPort': listener['Listener']['LoadBalancerPort']
            }],
            'healthCheck': health_check['Target'],
            'healthCheckProtocol': 'HTTP',
            'healthCheckPort': listener['Listener']['LoadBalancerPort'],
            'healthCheckPath': '/',
            'healthTimeout': health_check['Timeout'],
            'healthInterval': health_check['Interval'],
            'healthyThreshold': health_check['HealthyThreshold'],
            'unhealthyThreshold': health_check['UnhealthyThreshold'],

            'user': '[anonymous]',
            'usePreferredZones': True,
            'vpcId': vpc_id,
            'subnetType': subnet_type,
            # If I set security group to this then I get an error it is missing.
            # bindings['TEST_AWS_SECURITY_GROUP_ID']],
            'securityGroups': security_groups
        }],
        description='Create Load Balancer: ' + load_balancer_name,
        application=self.TEST_APP)

    builder = aws.AwsContractBuilder(self.aws_observer)
    (builder.new_clause_builder('Load Balancer Added', retryable_for_secs=10)
     .collect_resources(
         aws_module='elb',
         command='describe-load-balancers',
         args=['--load-balancer-names', load_balancer_name])
     .contains_path_match(
        'LoadBalancerDescriptions',
        {'HealthCheck': jp.DICT_MATCHES({
              key: jp.EQUIVALENT(value) for key, value in health_check.items()}),
         'AvailabilityZones': jp.LIST_SIMILAR(expect_avail_zones),
         'ListenerDescriptions/Listener':
             jp.DICT_MATCHES({key: jp.NUM_EQ(value)
                              for key, value in listener['Listener'].items()})
         })
    )

    title_decorator = '_with_vpc' if use_vpc else '_without_vpc'
    return st.OperationContract(
        self.new_post_operation(
            title='upsert_load_balancer' + title_decorator,
            data=payload,
            path='tasks'),
        contract=builder.build())

  def delete_load_balancer(self, use_vpc):
    """Creates OperationContract for deleteLoadBalancer.

    To verify the operation, we just check that the AWS resources
    created by upsert_load_balancer are no longer visible on AWS.

    Args:
      use_vpc: [bool] if True delete the VPC load balancer, otherwise
         the non-VPC load balancer.
    """
    load_balancer_name = self.lb_name
    if not use_vpc:
      # This is the second load balancer, where we decorated the name in upsert.
      load_balancer_name += '-pub'

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'type': 'deleteLoadBalancer',
            'cloudProvider': 'aws',

            'credentials': self.bindings['SPINNAKER_AWS_ACCOUNT'],
            'regions': [self.bindings['TEST_AWS_REGION']],
            'loadBalancerName': load_balancer_name
        }],
        description='Delete Load Balancer: {0} in {1}:{2}'.format(
            load_balancer_name,
            self.bindings['SPINNAKER_AWS_ACCOUNT'],
            self.bindings['TEST_AWS_REGION']),
        application=self.TEST_APP)

    builder = aws.AwsContractBuilder(self.aws_observer)
    (builder.new_clause_builder('Load Balancer Removed')
     .collect_resources(
         aws_module='elb',
         command='describe-load-balancers',
         args=['--load-balancer-names', load_balancer_name],
         no_resources_ok=True)
     .excludes_path_value('LoadBalancerName', load_balancer_name))

    title_decorator = '_with_vpc' if use_vpc else '_without_vpc'
    return st.OperationContract(
        self.new_post_operation(
            title='delete_load_balancer' + title_decorator,
            data=payload,
            path='tasks'),
        contract=builder.build())

  def create_server_group(self):
    """Creates OperationContract for createServerGroup.

    To verify the operation, we just check that the AWS Auto Scaling Group
    for the server group was created.
    """
    bindings = self.bindings

    # Spinnaker determines the group name created,
    # which will be the following:
    group_name = '{app}-{stack}-v000'.format(
        app=self.TEST_APP, stack=bindings['TEST_STACK'])

    region = bindings['TEST_AWS_REGION']
    avail_zones = [region + 'a', region + 'b']

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'type': 'createServerGroup',
            'cloudProvider': 'aws',
            'application': self.TEST_APP,
            'credentials': bindings['SPINNAKER_AWS_ACCOUNT'],
            'strategy':'',
            'capacity': {'min':2, 'max':2, 'desired':2},
            'targetHealthyDeployPercentage': 100,
            'loadBalancers': [self.lb_name],
            'cooldown': 8,
            'healthCheckType': 'EC2',
            'healthCheckGracePeriod': 40,
            'instanceMonitoring': False,
            'ebsOptimized': False,
            'iamRole': bindings['AWS_IAM_ROLE'],
            'terminationPolicies': ['Default'],

            'availabilityZones': {region: avail_zones},
            'keyPair': bindings['SPINNAKER_AWS_ACCOUNT'] + '-keypair',
            'suspendedProcesses': [],
            # TODO(ewiseblatt): Inquiring about how this value is determined.
            # It seems to be the "Name" tag value of one of the VPCs
            # but is not the default VPC, which is what we using as the VPC_ID.
            # So I suspect something is out of whack. This name comes from
            # spinnaker.io tutorial. But using the default vpc would probably
            # be more adaptive to the particular deployment.
            'subnetType': 'internal (defaultvpc)',
            'securityGroups': [bindings['TEST_AWS_SECURITY_GROUP_ID']],
            'virtualizationType': 'paravirtual',
            'stack': bindings['TEST_STACK'],
            'freeFormDetails': '',
            'amiName': bindings['TEST_AWS_AMI'],
            'instanceType': 'm1.small',
            'useSourceCapacity': False,
            'account': bindings['SPINNAKER_AWS_ACCOUNT'],
            'user': '[anonymous]'
        }],
        description='Create Server Group in ' + group_name,
        application=self.TEST_APP)

    builder = aws.AwsContractBuilder(self.aws_observer)
    (builder.new_clause_builder('Auto Server Group Added',
                                retryable_for_secs=30)
     .collect_resources('autoscaling', 'describe-auto-scaling-groups',
                        args=['--auto-scaling-group-names', group_name])
     .contains_path_match('AutoScalingGroups', {'MaxSize': jp.NUM_EQ(2)}))

    return st.OperationContract(
        self.new_post_operation(
            title='create_server_group', data=payload, path='tasks'),
        contract=builder.build())

  def delete_server_group(self):
    """Creates OperationContract for deleteServerGroup.

    To verify the operation, we just check that the AWS Auto Scaling Group
    is no longer visible on AWS (or is in the process of terminating).
    """
    bindings = self.bindings

    group_name = '{app}-{stack}-v000'.format(
        app=self.TEST_APP, stack=bindings['TEST_STACK'])

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'aws',
            'type': 'destroyServerGroup',
            'serverGroupName': group_name,
            'asgName': group_name,
            'region': bindings['TEST_AWS_REGION'],
            'regions': [bindings['TEST_AWS_REGION']],
            'credentials': bindings['SPINNAKER_AWS_ACCOUNT'],
            'user': '[anonymous]'
        }],
        application=self.TEST_APP,
        description='DestroyServerGroup: ' + group_name)

    builder = aws.AwsContractBuilder(self.aws_observer)
    (builder.new_clause_builder('Auto Scaling Group Removed')
     .collect_resources('autoscaling', 'describe-auto-scaling-groups',
                        args=['--auto-scaling-group-names', group_name],
                        no_resources_ok=True)
     .contains_path_match('AutoScalingGroups', {'MaxSize': jp.NUM_EQ(0)}))

    (builder.new_clause_builder('Instances Are Removed',
                                retryable_for_secs=30)
     .collect_resources('ec2', 'describe-instances', no_resources_ok=True)
     .excludes_path_value('name', group_name))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_server_group', data=payload, path='tasks'),
        contract=builder.build())


class AwsSmokeTest(st.AgentTestCase):
  """The test fixture for the SmokeTest.

  This is implemented using citest OperationContract instances that are
  created by the AwsSmokeTestScenario.
  """
  # pylint: disable=missing-docstring

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        AwsSmokeTestScenario)

  @property
  def testing_agent(self):
    scenario = self.scenario
    return self.scenario.agent

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  def test_b_upsert_load_balancer_public(self):
    self.run_test_case(self.scenario.upsert_load_balancer(use_vpc=False))

  def test_b_upsert_load_balancer_vpc(self):
    self.run_test_case(self.scenario.upsert_load_balancer(use_vpc=True))

  def test_c_create_server_group(self):
    # We'll permit this to timeout for now
    # because it might be waiting on confirmation
    # but we'll continue anyway because side effects
    # should have still taken place.
    self.run_test_case(self.scenario.create_server_group(), timeout_ok=True)

  def test_x_delete_server_group(self):
    self.run_test_case(self.scenario.delete_server_group(), max_retries=5)

  def test_y_delete_load_balancer_vpc(self):
    self.run_test_case(self.scenario.delete_load_balancer(use_vpc=True),
                       max_retries=5)

  def test_y_delete_load_balancer_pub(self):
    self.run_test_case(self.scenario.delete_load_balancer(use_vpc=False),
                       max_retries=5)

  def test_z_delete_app(self):
    # Give a total of a minute because it might also need
    # an internal cache update
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  """Implements the main method running this smoke test."""

  defaults = {
      'TEST_STACK': str(AwsSmokeTestScenario.DEFAULT_TEST_ID),
      'TEST_APP': 'smoketest' + AwsSmokeTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[AwsSmokeTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[AwsSmokeTest])


if __name__ == '__main__':
  sys.exit(main())
