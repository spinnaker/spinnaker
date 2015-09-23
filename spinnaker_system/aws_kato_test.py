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

# Sample Usage:
#     Assuming you have created $PASSPHRASE_FILE (which you should chmod 400):
#     and $CITEST_ROOT points to the root directory of this repository
#     (which is . if you execute this from the root)
#     and $AWS_PROFILE is the name of the aws_cli profile for authenticating
#     to observe aws resources:
#
#     This first command would be used if Spinnaker itself was deployed on GCE.
#     The test needs to talk to GCE to get to spinnaker (using the gce_* params)
#     then talk to AWS (using the aws_profile with the aws cli program) to
#     verify Spinnaker had the right effects on AWS.
#
#     PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
#       python $CITEST_ROOT/spinnaker/spinnaker_system/aws_kato_test.py \
#       --gce_ssh_passphrase_file=$PASSPHRASE_FILE \
#       --gce_project=$PROJECT \
#       --gce_zone=$GCE_ZONE \
#       --gce_instance=$INSTANCE \
#       --test_aws_zone=$AWS_ZONE \
#       --aws_profile=$AWS_PROFILE
#
#   or
#
#     This second command would be used if Spinnaker itself was deployed some
#     place reachable through a direct IP connection. It could be, but is not
#     necessarily deployed on GCE. It is similar to above except it does not
#     need to go through GCE and its firewalls to locate the actual IP endpoints
#     rather those are already known and accessible.
#
#     PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
#       python $CITEST_ROOT/spinnaker/spinnaker_system/aws_kato_test.py \
#       --native_hostname=host-running-kato
#       --test_aws_zone=$AWS_ZONE \
#       --aws_profile=$AWS_PROFILE
#
#   Note that the $AWS_ZONE is not directly used, rather it is a standard
#   parameter being used to infer the region. The test is going to pick
#   some different availability zones within the region in order to test kato.
#   These are currently hardcoded in.

import sys
import time

import citest.aws_testing as aws
import citest.gcp_testing as gcp
import citest.json_contract as jc
import citest.service_testing as st

import spinnaker_testing as sk
import spinnaker_testing.kato as kato


_TEST_DECORATOR = time.strftime('%H%M%S')


class AwsKatoTestScenario(sk.SpinnakerTestScenario):
  @classmethod
  def new_agent(cls, bindings):
    return kato.new_agent(bindings)

  @classmethod
  def initArgumentParser(cls, parser):
    """Initialize command line argument parser.

    Args:
      parser: argparse.ArgumentParser
    """
    super(AwsKatoTestScenario, cls).initArgumentParser(parser, 'aws_kato')

    # Local test
    parser.add_argument(
        '--test_image_name',
        default='ubuntu-1404-trusty-v20150316',
        help='Image name to use when creating test instance.')
    parser.add_argument(
        '--test_stack', default='awstest', help='Spinnaker stack decorator.')
    parser.add_argument(
        '--test_app', default='myaws', help='')

  def __init__(self, bindings, agent):
    super(AwsKatoTestScenario, self).__init__(bindings, agent)

  def upsert_network_load_balancer(self):
    detail_raw_name = 'katotestlb' + _TEST_DECORATOR
    self._use_lb_name = detail_raw_name

    region = self.bindings['TEST_AWS_REGION']
    avail_zones = [region + 'a', region + 'b']
    avail_zone_str = str(avail_zones).replace("'", '"')

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
    path = 'healthcheck'

    # Note the double-{ is escaping the curly.
    # A single curly are for format variable substitution.
    payload = self.substitute_variables(
      '[{{'
          '"upsertAmazonLoadBalancerDescription":'
          '{{'
             '"credentials":"$AWS_CREDENTIALS",'
             '"clusterName":"$TEST_APP",'
             '"name":"{name}",'
             '"availabilityZones":{{"$TEST_AWS_REGION":{availability_zones} }},'
             '"listeners":[{{'
                '"internalProtocol":"HTTP","internalPort":{internal_port},'
                '"externalProtocol":"HTTP","externalPort":{external_port}'
             '}}],'
             '"healthCheck":"{target}",'
             '"healthTimeout":{timeout},'
             '"healthInterval":{interval},'
             '"healthyThreshold":{healthy},'
             '"unhealthyThreshold":{unhealthy}'
          '}}'
      '}}]'.format(
          name=detail_raw_name,
          availability_zones=avail_zone_str,
          internal_port=listener['Listener']['InstancePort'],
          external_port=listener['Listener']['LoadBalancerPort'],
          target=health_check['Target'],
          timeout=health_check['Timeout'], interval=health_check['Interval'],
          healthy=health_check['HealthyThreshold'],
          unhealthy=health_check['UnhealthyThreshold']))


    builder = aws.AwsContractBuilder(self.aws_observer)
    (builder.new_clause_builder('Load Balancer Added', retryable_for_secs=30)
       .collect_resources(
           aws_module='elb',
           command='describe-load-balancers',
           args=['--load-balancer-names', self._use_lb_name])
       .contains_group(
           [jc.PathContainsPredicate(
               'LoadBalancerDescriptions/HealthCheck', health_check),
            jc.PathEqPredicate(
               'LoadBalancerDescriptions/AvailabilityZones', avail_zones),
            jc.PathElementsContainPredicate(
               'LoadBalancerDescriptions/ListenerDescriptions', listener)]))

    return st.OperationContract(
        self.new_post_operation(
            title='upsert_amazon_load_balancer', data=payload, path='ops'),
        contract=builder.build())


  def delete_network_load_balancer(self):
    payload = self.substitute_variables(
      '[{'
          '"deleteAmazonLoadBalancerDescription":{'
          '"credentials":"$AWS_CREDENTIALS",'
          '"regions":["$TEST_AWS_REGION"],'
          '"loadBalancerName":"' + self._use_lb_name + '"}'
      '}]')

    builder = aws.AwsContractBuilder(self.aws_observer)
    (builder.new_clause_builder('Load Balancer Removed')
        .collect_resources(
            aws_module='elb',
            command='describe-load-balancers',
            args=['--load-balancer-names', self._use_lb_name],
            no_resources_ok=True)
        .excludes('LoadBalancerName', self._use_lb_name))

    return st.OperationContract(
      self.new_post_operation(
          title='delete_amazon_load_balancer', data=payload, path='ops'),
      contract=builder.build())


class AwsKatoIntegrationTest(st.AgentTestCase):
  def test_a_upsert_network_load_balancer(self):
    self.run_test_case(self.scenario.upsert_network_load_balancer())

  def test_z_delete_network_load_balancer(self):
    self.run_test_case(self.scenario.delete_network_load_balancer())


def main():
    AwsKatoIntegrationTest.main(AwsKatoTestScenario)


if __name__ == '__main__':
  main()
  sys.exit(0)
