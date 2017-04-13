# Copyright 2015 Google Inc. All Rights Reserved.
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
Tests to see if CloudDriver/Kato can interoperate with Amazon Web Services.

Sample Usage:
    Assuming you have created $PASSPHRASE_FILE (which you should chmod 400):
    and $CITEST_ROOT points to the root directory of this repository
    (which is . if you execute this from the root)
    and $AWS_PROFILE is the name of the aws_cli profile for authenticating
    to observe aws resources:

    This first command would be used if Spinnaker itself was deployed on GCE.
    The test needs to talk to GCE to get to spinnaker (using the gce_* params)
    then talk to AWS (using the aws_profile with the aws cli program) to
    verify Spinnaker had the right effects on AWS.

    PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
       python $CITEST_ROOT/spinnaker/spinnaker_system/aws_kato_test.py \
       --gce_ssh_passphrase_file=$PASSPHRASE_FILE \
       --gce_project=$PROJECT \
       --gce_zone=$GCE_ZONE \
       --gce_instance=$INSTANCE \
       --test_aws_zone=$AWS_ZONE \
       --aws_profile=$AWS_PROFILE

   or

     This second command would be used if Spinnaker itself was deployed some
     place reachable through a direct IP connection. It could be, but is not
     necessarily deployed on GCE. It is similar to above except it does not
     need to go through GCE and its firewalls to locate the actual IP endpoints
     rather those are already known and accessible.

     PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
       python $CITEST_ROOT/spinnaker/spinnaker_system/aws_kato_test.py \
       --native_hostname=host-running-kato
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
import citest.base
import citest.aws_testing as aws
import citest.json_predicate as jp
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.kato as kato


class AwsKatoTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the test.

  This scenario defines the different test operations.
  We're going to:
    Create a Load Balancer
    Delete a Load Balancer
  """

  __use_lb_name = ''     # The load balancer name.

  @classmethod
  def new_agent(cls, bindings):
    """Implements the base class interface to create a new agent.

    This method is called by the base classes during setup/initialization.

    Args:
      bindings: The bindings dictionary with configuration information
        that this factory can draw from to initialize. If the factory would
        like additional custom bindings it could add them to initArgumentParser.

    Returns:
      A citest.service_testing.BaseAgent that can interact with Kato.
      This is the agent that test operations will be posted to.
    """
    return kato.new_agent(bindings)

  def __init__(self, bindings):
    """Initialize scenario."""
    super(AwsKatoTestScenario, self).__init__(bindings)
    self.elb_client = self.aws_observer.make_boto_client('elb')


  def upsert_load_balancer(self):
    """Creates OperationContract for upsertLoadBalancer.

    Calls Spinnaker's upsertLoadBalancer with a configuration, then verifies
    that the expected resources and configurations are visible on AWS. See
    the contract builder for more info on what the expectations are.
    """
    detail_raw_name = 'katotestlb' + self.test_id
    self.__use_lb_name = detail_raw_name

    bindings = self.bindings
    region = bindings['TEST_AWS_REGION']
    avail_zones = [region + 'a', region + 'b']

    listener = {
        'Listener': {
            'InstancePort':7001,
            'LoadBalancerPort':80
        }
    }
    health_check = {
        'HealthyThreshold':8,
        'UnhealthyThreshold':3,
        'Interval':123,
        'Timeout':12,
        'Target':'HTTP:%d/healthcheck' % listener['Listener']['InstancePort']
    }

    payload = self.agent.type_to_payload(
        'upsertAmazonLoadBalancerDescription',
        {
            'credentials': bindings['SPINNAKER_AWS_ACCOUNT'],
            'clusterName': bindings['TEST_APP'],
            'name': detail_raw_name,
            'availabilityZones': {region: avail_zones},
            'listeners': [{
                'internalProtocol': 'HTTP',
                'internalPort': listener['Listener']['InstancePort'],
                'externalProtocol': 'HTTP',
                'externalPort': listener['Listener']['LoadBalancerPort']
            }],
            'healthCheck': health_check['Target'],
            'healthTimeout': health_check['Timeout'],
            'healthInterval': health_check['Interval'],
            'healthyThreshold': health_check['HealthyThreshold'],
            'unhealthyThreshold': health_check['UnhealthyThreshold']
        })

    builder = aws.AwsPythonContractBuilder(self.aws_observer)
    (builder.new_clause_builder('Load Balancer Added', retryable_for_secs=30)
     .call_method(
         self.elb_client.describe_load_balancers,
         LoadBalancerNames=[self.__use_lb_name])
     .contains_path_match(
         'LoadBalancerDescriptions', {
             'HealthCheck': jp.DICT_MATCHES(
                 {key: jp.EQUIVALENT(value)
                  for key, value in health_check.items()}),
             'AvailabilityZones':
                 jp.LIST_MATCHES([jp.STR_SUBSTR(zone) for zone in avail_zones]),
             'ListenerDescriptions/Listener': jp.DICT_MATCHES(
                 {key: jp.EQUIVALENT(value)
                  for key, value in listener['Listener'].items()})
             })
    )


    return st.OperationContract(
        self.new_post_operation(
            title='upsert_amazon_load_balancer', data=payload, path='ops'),
        contract=builder.build())

  def delete_load_balancer(self):
    """Creates OperationContract for deleteLoadBalancer.

    To verify the operation, we just check that the AWS resources
    created by upsert_load_balancer are no longer visible on AWS.
    """
    region = self.bindings['TEST_AWS_REGION']
    payload = self.agent.type_to_payload(
        'deleteAmazonLoadBalancerDescription',
        {
            'credentials': self.bindings['SPINNAKER_AWS_ACCOUNT'],
            'regions': [region],
            'loadBalancerName': self.__use_lb_name
        })

    builder = aws.AwsPythonContractBuilder(self.aws_observer)
    (builder.new_clause_builder('Load Balancer Removed')
     .call_method(
         self.elb_client.describe_load_balancers,
         LoadBalancerNames=[self.__use_lb_name])
     .append_verifier(
         aws.AwsErrorVerifier('ExpectError', 'LoadBalancerNotFound')))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_amazon_load_balancer', data=payload, path='ops'),
        contract=builder.build())


class AwsKatoIntegrationTest(st.AgentTestCase):
  """The test fixture for the SmokeTest.

  This is implemented using citest OperationContract instances that are
  created by the AwsKatoTestScenario.
  """
  # pylint: disable=missing-docstring

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        AwsKatoTestScenario)

  @property
  def testing_agent(self):
    return self.scenario.agent

  def test_a_upsert_load_balancer(self):
    self.run_test_case(self.scenario.upsert_load_balancer())

  def test_z_delete_load_balancer(self):
    self.run_test_case(self.scenario.delete_load_balancer())


def main():
  """Implements the main method running this smoke test."""

  defaults = {
      'TEST_APP': 'awskatotest' + AwsKatoTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[AwsKatoTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[AwsKatoIntegrationTest])


if __name__ == '__main__':
  sys.exit(main())
