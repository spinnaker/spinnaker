# Copyright 2017 Google Inc. All Rights Reserved.
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

"""Amazon Web Services platform and test support for SpinnakerTestScenario."""

import logging

from citest.base import ExecutionContext
import citest.aws_testing as aws
from spinnaker_testing.base_scenario_support import BaseScenarioPlatformSupport


class AwsScenarioSupport(BaseScenarioPlatformSupport):
  """Provides SpinnakerScenarioSupport for Amazon Web Services."""

  @classmethod
  def add_commandline_parameters(cls, scenario_class, builder, defaults):
    """Implements BaseScenarioPlatformSupport interface.

    Args:
      scenario_class: [class spinnaker_testing.SpinnakerTestScenario]
      builder: [citest.base.ConfigBindingsBuilder]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    #
    # Observer parameters
    #
    builder.add_argument(
        '--aws_profile', default=defaults.get('AWS_PROFILE', None),
        help='aws command-line tool --profile parameter when observing AWS.')

    #
    # Operation Parameters
    #
    builder.add_argument(
        '--aws_credentials',
        dest='spinnaker_aws_account',
        help='DEPRECATED. Replaced by --spinnaker_aws_account')

    builder.add_argument(
        '--spinnaker_aws_account',
        default=defaults.get('SPINNAKER_AWS_ACCOUNT', None),
        help='Spinnaker account name to use for test operations against AWS.'
             ' Only used when managing resources on AWS.')

    builder.add_argument(
        '--aws_iam_role', default=defaults.get('AWS_IAM_ROLE', None),
        help='Spinnaker IAM role name for test operations.'
             ' Only used when managing jobs running on AWS.')

    builder.add_argument(
        '--test_aws_ami',
        default=defaults.get(
            'TEST_AWS_AMI',
            'bitnami-tomcatstack-7.0.63-1-linux-ubuntu-14.04.1-x86_64-ebs'),
        help='Default Amazon AMI to use when creating test instances.'
             ' The default image will listen on port 80.')

    builder.add_argument(
        '--test_aws_region',
        default=defaults.get('TEST_AWS_REGION', ''),
        help='The GCE region to test generated instances in (when managing'
             ' AWS). If not specified, then derive it fro --test_aws_zone.')

    builder.add_argument(
        '--test_aws_security_group_id',
        default=defaults.get('TEST_AWS_SECURITY_GROUP_ID', None),
        help='Default AWS SecurityGroupId when creating test resources.')

    builder.add_argument(
        '--test_aws_zone',
        default=defaults.get('TEST_AWS_ZONE', 'us-east-1c'),
        help='The AWS zone to test generated instances in (when managing AWS).'
             ' This implies the AWS region as well.')

    builder.add_argument(
        '--test_aws_vpc_id',
        default=defaults.get('TEST_AWS_VPC_ID', None),
        help='Default AWS VpcId to use when creating test resources.')

    builder.add_argument(
        '--test_aws_keypair',
        default=defaults.get('TEST_AWS_KEYPAIR', None),
        help='AWS KeyPair for when one is needed by a test.')

  def _make_observer(self):
    """Implements BaseScenarioPlatformSupport interface."""
    bindings = self.scenario.bindings
    profile = bindings.get('AWS_PROFILE')
    if not profile:
      raise ValueError('An AWS Observer requires an AWS_PROFILE')

    return aws.AwsPythonAgent(profile)

  def __init__(self, scenario):
    """Constructor.

    Args:
      scenario: [SpinnakerTestScenario] The scenario being supported.
    """
    super(AwsScenarioSupport, self).__init__("aws", scenario)
    self.__aws_observer = None

    bindings = scenario.bindings

    if not bindings['AWS_IAM_ROLE']:
      bindings['AWS_IAM_ROLE'] = scenario.agent.deployed_config.get(
          'providers.aws.defaultIAMRole', None)

    if not bindings['TEST_AWS_ZONE']:
      bindings['TEST_AWS_ZONE'] = bindings['AWS_ZONE']

    if not bindings.get('TEST_AWS_REGION', ''):
      bindings['TEST_AWS_REGION'] = bindings['TEST_AWS_ZONE'][:-1]

    if not bindings.get('TEST_AWS_KEYPAIR', ''):
      bindings['TEST_AWS_KEYPAIR'] = '{0}-keypair'.format(
          bindings['SPINNAKER_AWS_ACCOUNT'])

    bindings.add_lazy_initializer(
        'TEST_AWS_VPC_ID', self.__lazy_binding_initializer)
    bindings.add_lazy_initializer(
        'TEST_AWS_SECURITY_GROUP_ID', self.__lazy_binding_initializer)

  def __lazy_binding_initializer(self, bindings, key):
    normalized_key = key.upper()

    ec2_client = None

    if normalized_key == 'TEST_AWS_VPC_ID':
      # We need to figure out a specific aws vpc id to use.
      logger = logging.getLogger(__name__)
      logger.info('Determine default AWS VpcId...')
      if not ec2_client:
        ec2_client = self.observer.make_boto_client('ec2')

      # If we want the name defaultvpc then do this
      # filters = [{'Name': 'tag:Name', 'Values': ['defaultvpc']}]
      # I think we want the VPC that is the default
      # filters = [{'Name': 'isDefault', 'Values': ['true']}]
      filters = [{'Name': 'tag:Name', 'Values': ['defaultvpc']}]

      response = self.observer.call_method(
          ExecutionContext(),
          ec2_client.describe_vpcs,
          Filters=filters)

      vpc_list = response['Vpcs']
      if not vpc_list:
        raise ValueError('There is no vpc matching filter {0}'.format(filters))

      found = vpc_list[0]['VpcId']
      logger.info('Using discovered default VpcId=%s', str(found))
      return found

    if normalized_key == 'TEST_AWS_SECURITY_GROUP_ID':
      # We need to figure out a specific security group that is compatable
      # with the VpcId we are using.
      logger = logging.getLogger(__name__)
      logger.info('Determine default AWS SecurityGroupId...')
      if not ec2_client:
        ec2_client = self.observer.make_boto_client('ec2')
      response = self.observer.call_method(
          ExecutionContext(),
          ec2_client.describe_security_groups)
      sg_list = response['SecurityGroups']

      found = None
      vpc_id = bindings['TEST_AWS_VPC_ID']
      for entry in sg_list:
        if entry.get('VpcId', None) == vpc_id:
          found = entry['GroupId']
          break
      if not found:
        raise ValueError('Could not find a security group for AWS_VPC_ID {0}'
                         .format(vpc_id))

      logger.info('Using discovered default SecurityGroupId=%s', str(found))
      return found

    raise KeyError(key)
