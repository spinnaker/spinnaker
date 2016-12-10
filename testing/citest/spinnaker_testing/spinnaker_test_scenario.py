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


"""Specialization of AgentTestScenario to facilitate testing Spinnaker.

This provides means for locating spinnaker and extracting configuration
information so that the tests can adapt to the deployment information
to make appropriate observations.
"""

import logging

from citest.base import (
    ExecutionContext,
    JournalLogger)
    
import citest.service_testing as sk
import citest.service_testing.http_agent as http_agent
import citest.aws_testing as aws
import citest.gcp_testing as gcp
import citest.kube_testing as kube


class SpinnakerTestScenario(sk.AgentTestScenario):
  """Specialization of AgentTestScenario to facilitate testing Spinnaker.

  Adds standard command line arguments for locating the deployed system, and
  setting up observers.
  """

  @classmethod
  def new_post_operation(cls, title, data, path, status_class=None):
    """Creates an operation that posts data to the given path when executed.

    The base_url will come from the agent that the operation is eventually
    executed on.

    Args:
      title: [string] The name of the operation for reporting purposes.
      data: [string] The payload to send in the HTTP POST.
      path: [string] The path relative to the base url provided later.
      status_class: [class AgentOperationStatus] If provided, a specialization
         of the AgentOperationStatus to use for tracking the execution.
    """
    return http_agent.HttpPostOperation(title=title, data=data, path=path,
                                        status_class=status_class)

  @classmethod
  def new_put_operation(cls, title, data, path, status_class=None):
    """Creates an operation that puts data to the given path when executed.

    The base_url will come from the agent that the operation is eventually
    executed on.

    Args:
      title: [string] The name of the operation for reporting purposes.
      data: [string] The payload to send in the HTTP PUT.
      path: [string] The path relative to the base url provided later.
      status_class: [class AgentOperationStatus] If provided, a specialization
         of the AgentOperationStatus to use for tracking the execution.
    """
    return http_agent.HttpPutOperation(title=title, data=data, path=path,
                                       status_class=status_class)

  @classmethod
  def new_patch_operation(cls, title, data, path, status_class=None):
    """Creates an operation that patches data to the given path when executed.

    The base_url will come from the agent that the operation is eventually
    executed on.

    Args:
      title: [string] The name of the operation for reporting purposes.
      data: [string] The payload to send in the HTTP PATCH.
      path: [string] The path relative to the base url provided later.
      status_class: [class AgentOperationStatus] If provided, a specialization
         of the AgentOperationStatus to use for tracking the execution.
    """
    return http_agent.HttpPatchOperation(title=title, data=data, path=path,
                                         status_class=status_class)

  @classmethod
  def new_delete_operation(cls, title, data, path, status_class=None):
    """Creates an operation that deletes from the given path when executed.

    The base_url will come from the agent that the operation is eventually
    executed on.

    Args:
      title: [string] The name of the operation for reporting purposes.
      data: [string] The payload to send in the HTTP DELETE.
      path: [string] The path relative to the base url provided later.
      status_class: [class AgentOperationStatus] If provided, a specialization
         of the AgentOperationStatus to use for tracking the execution.
    """
    return http_agent.HttpDeleteOperation(title=title, data=data, path=path,
                                          status_class=status_class)

  @classmethod
  def _initSpinnakerLocationParameters(cls, parser, defaults):
    """Initialize arguments for locating spinnaker itself.
    
      parser: [argparse.ArgumentParser]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    subsystem_name = 'the server to test'

    # This could probably be removed fairly easily.
    # It probably isnt useful anymore.
    parser.add_argument(
        '--host_platform', default=defaults.get('HOST_PLATFORM', None),
        help='Platform running spinnaker (gce, native).'
             ' If this is not explicitly set, then try to'
             ' guess based on other parameters set.')

    parser.add_argument(
        '--native_hostname', default=defaults.get('NATIVE_HOSTNAME', None),
        help='Host name that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is "native".'.format(system=subsystem_name))
    parser.add_argument(
        '--native_port', default=defaults.get('NATIVE_PORT', None),
        help='Port number that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is "native". It is not needed if the system is using its'
             ' standard port.'.format(system=subsystem_name))

    parser.add_argument(
        '--gce_project', default=defaults.get('GCE_PROJECT', None),
        help='The GCE project that {system} is running within.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=subsystem_name))
    parser.add_argument(
        '--gce_zone', default=defaults.get('GCE_ZONE', None),
        help='The GCE zone that {system} is running within.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=subsystem_name))
    parser.add_argument(
        '--gce_instance', default=defaults.get('GCE_INSTANCE', None),
        help='The GCE instance name that {system} is running on.'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.'.format(system=subsystem_name))
    parser.add_argument(
        '--gce_ssh_passphrase_file',
        default=defaults.get('GCE_SSH_PASSPHRASE_FILE', None),
        help='Specifying a file containing the SSH passphrase'
             ' will permit tunneling or the execution of remote'
             ' commands into the --gce_instance if needed.')

    # TODO(ewiseblatt): 20160923
    # This is probably obsoleted. It is only used by the gcloud agent,
    # which is only used to establish a tunnel. I dont think the credentials
    # are needed there, but your ssh passphrase is (which is unrelated).
    parser.add_argument(
        '--gce_service_account',
        default=defaults.get('GCE_SERVICE_ACCOUNT', None),
        help='The GCE service account to use when interacting with the'
             ' gce_instance. The default will be the default configured'
             ' account on the local machine. To change the default account,'
             ' use "gcloud config set account". To active service accounts,'
             ' use "gcloud auth activate-service-account".'
             ' This parameter is only used if the spinnaker host platform'
             ' is GCE.')

  @classmethod
  def _initGoogleOperationConfigurationParameters(cls, parser, defaults):
    """Initialize arguments for configuring operations for google.

      parser: [argparse.ArgumentParser]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    parser.add_argument(
        '--spinnaker_google_account',
        default=defaults.get('SPINNAKER_GOOGLE_ACCOUNT', None),
        help='Spinnaker account name to use for test operations against GCE.'
             ' Only used when managing resources on GCE.'
             ' If left empty then use the configured primary account.')
    parser.add_argument(
        '--gce_credentials',
        dest='spinnaker_google_account',
        help='DEPRECATED. Replaced by --spinnaker_google_account')
    parser.add_argument(
        '--managed_gce_project', dest='google_primary_managed_project_id',
        help='GCE project to test instances in (when managing GCE).')
    parser.add_argument(
        '--test_gce_zone',
        default=defaults.get('TEST_GCE_ZONE', 'us-central1-f'),
        help='The GCE zone to test generated instances in (when managing GCE).'
             ' This implies the GCE region as well.')
    parser.add_argument(
        '--test_gce_region',
        default=defaults.get('TEST_GCE_REGION', ''),
        help='The GCE region to test generated instances in (when managing'
             ' GCE). If not specified, then derive it from --test_gce_zone.')
    parser.add_argument(
        '--test_gce_image_name',
        default=defaults.get('TEST_GCE_IMAGE_NAME',
                             'ubuntu-1404-trusty-v20160919'),
        help='Default Google Compute Engine image name to use when'
             ' creating test instances.')

  @classmethod
  def _initAwsOperationConfigurationParameters(cls, parser, defaults):
    """Initialize arguments for configuring operations for aws.

      parser: [argparse.ArgumentParser]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    parser.add_argument(
        '--spinnaker_aws_account',
        default=defaults.get('SPINNAKER_AWS_ACCOUNT', None),
        help='Spinnaker account name to use for test operations against AWS.'
             ' Only used when managing resources on AWS.')
    parser.add_argument(
        '--aws_credentials',
        dest='spinnaker_aws_account',
        help='DEPRECATED. Replaced by --spinnaker_aws_account')

    parser.add_argument(
        '--aws_iam_role', default=defaults.get('AWS_IAM_ROLE', None),
        help='Spinnaker IAM role name for test operations.'
             ' Only used when managing jobs running on AWS.')
    parser.add_argument(
        '--test_aws_zone',
        default=defaults.get('TEST_AWS_ZONE', 'us-east-1c'),
        help='The AWS zone to test generated instances in (when managing AWS).'
             ' This implies the AWS region as well.')
    parser.add_argument(
        '--test_aws_region',
        default=defaults.get('TEST_AWS_REGION', ''),
        help='The GCE region to test generated instances in (when managing'
             ' AWS). If not specified, then derive it fro --test_aws_zone.')
    parser.add_argument(
        '--test_aws_ami',
        default=defaults.get(
            'TEST_AWS_AMI',
            'bitnami-tomcatstack-7.0.63-1-linux-ubuntu-14.04.1-x86_64-ebs'),
        help='Default Amazon AMI to use when creating test instances.'
             ' The default image will listen on port 80.')
    parser.add_argument(
        '--test_aws_vpc_id',
        default=defaults.get('TEST_AWS_VPC_ID', None),
        help='Default AWS VpcId to use when creating test resources.')
    parser.add_argument(
        '--test_aws_security_group_id',
        default=defaults.get('TEST_AWS_SECURITY_GROUP_ID', None),
        help='Default AWS SecurityGroupId when creating test resources.')

  @classmethod
  def _initKubeOperationConfigurationParameters(cls, parser, defaults):
    """Initialize arguments for configuring operations for kubernetes.

      parser: [argparse.ArgumentParser]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    parser.add_argument(
        '--spinnaker_kubernetes_account',
        default=defaults.get('SPINNAKER_KUBERNETES_ACCOUNT', None),
        help='Spinnaker account name to use for test operations against'
             ' Kubernetes. Only used when managing jobs running on'
             ' Kubernetes.')

    parser.add_argument(
        '--kube_credentials',
        dest='spinnaker_kubernetes_account',
        help='DEPRECATED. Replaced by --spinnaker_kubernetes_account')

  @classmethod
  def _initOperationConfigurationParameters(cls, parser, defaults):
    """Initialize arguments for configuring operations and resources to create.
    
      parser: [argparse.ArgumentParser]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    parser.add_argument(
        '--test_stack', default=defaults.get('TEST_STACK', 'test'),
        help='Default Spinnaker stack decorator.')

    parser.add_argument(
        '--test_app', default=defaults.get('TEST_APP', cls.__name__.lower()),
        help='Default Spinnaker application name to use with test.')

    cls._initGoogleOperationConfigurationParameters(parser, defaults)
    cls._initAwsOperationConfigurationParameters(parser, defaults)
    cls._initKubeOperationConfigurationParameters(parser, defaults)

  @classmethod
  def _initObservationConfigurationParameters(cls, parser, defaults):
    """Initialize arguments for configuring observers.
    
      parser: [argparse.ArgumentParser]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    parser.add_argument(
        '--gce_credentials_path',
        default=defaults.get('GCE_CREDENTIALS_PATH', None),
        help='A path to the JSON file with credentials to use for observing'
             ' tests run against Google Cloud Platform.')

    parser.add_argument(
        '--aws_profile', default=defaults.get('AWS_PROFILE', None),
        help='aws command-line tool --profile parameter when observing AWS.')


  @classmethod
  def initArgumentParser(cls, parser, defaults=None):
    """Initialize command line argument parser.

    Args:
      parser: [argparse.ArgumentParser]
      defaults: [dict] Default binding value overrides.
         This is used to initialize the default commandline parameters.
    """
    super(SpinnakerTestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

    defaults = defaults or {}
    cls._initSpinnakerLocationParameters(parser, defaults=defaults)
    cls._initOperationConfigurationParameters(parser, defaults=defaults)
    cls._initObservationConfigurationParameters(parser, defaults=defaults)

  @property
  def gcp_observer(self):
    """The observer for inspecting GCE platform state, if configured."""
    return self.__gcp_observer

  @property
  def kube_observer(self):
    """The observer for inspecting Kubernetes platform state, if configured."""
    return self.__kube_observer

  @property
  def aws_observer(self):
    """The observer for inspecting AWS platform state, if configured."""
    return self.__aws_observer

  def __init__(self, bindings, agent=None):
    """Constructor

    Args:
      bindings: [dict] The parameter bindings for overriding the test
         scenario configuration.
      agent: [SpinnakerAgent] The Spinnaker agent to bind to the scenario.
    """
    super(SpinnakerTestScenario, self).__init__(bindings, agent)
    agent = self.agent
    self.__update_bindings_with_subsystem_configuration(agent)
    JournalLogger.begin_context('Configure Cloud Bindings')
    try:
      self.__init_google_bindings()
      self.__init_aws_bindings()
      self.__init_kubernetes_bindings()
      self._do_init_bindings()
    except:
      logger = logging.getLogger(__name__)
      logger.exception('Failed to initialize spinnaker agent.')
      raise
    finally:
      JournalLogger.end_context()

  def _do_init_bindings(self):
    pass

  def __init_aws_bindings(self):
    context = ExecutionContext()
    bindings = self.bindings  # base class made a copy
    if not bindings['TEST_AWS_ZONE']:
      bindings['TEST_AWS_ZONE'] = bindings['AWS_ZONE']
    if not bindings.get('TEST_AWS_REGION', ''):
      bindings['TEST_AWS_REGION'] = bindings['TEST_AWS_ZONE'][:-1]

    if bindings.get('AWS_PROFILE'):
      self.__aws_observer = aws.AwsAgent(
          bindings['AWS_PROFILE'], bindings['TEST_AWS_REGION'])
      if not bindings.get('TEST_AWS_VPC_ID', ''):
        # We need to figure out a specific aws vpc id to use.
        logger = logging.getLogger(__name__)
        logger.info('Determine default AWS VpcId...')
        vpc_list = self.__aws_observer.get_resource_list(
            context,
            root_key='Vpcs',
            aws_command='describe-vpcs',
            args=['--filters', 'Name=tag:Name,Values=defaultvpc'],
            region=bindings['TEST_AWS_REGION'],
            aws_module='ec2', profile=self.__aws_observer.profile)
        if not vpc_list:
          raise ValueError('There is no vpc tagged as "defaultvpc"')
        bindings['TEST_AWS_VPC_ID'] = vpc_list[0]['VpcId']
        logger.info('Using discovered default VpcId=%s',
                    str(bindings['TEST_AWS_VPC_ID']))

      if not bindings.get('TEST_AWS_SECURITY_GROUP', ''):
        # We need to figure out a specific security group that is compatable
        # with the VpcId we are using.
        logger = logging.getLogger(__name__)
        logger.info('Determine default AWS SecurityGroupId...')
        sg_list = self.__aws_observer.get_resource_list(
            context,
            root_key='SecurityGroups',
            aws_command='describe-security-groups', args=[],
            region=bindings['TEST_AWS_REGION'],
            aws_module='ec2', profile=self.__aws_observer.profile)
        for entry in sg_list:
          if entry.get('VpcId', None) == bindings['TEST_AWS_VPC_ID']:
            bindings['TEST_AWS_SECURITY_GROUP_ID'] = entry['GroupId']
            break
        logger.info('Using discovered default SecurityGroupId=%s',
                    str(bindings['TEST_AWS_SECURITY_GROUP_ID']))
    else:
      self.__aws_observer = None
      logger = logging.getLogger(__name__)
      logger.warning(
          '--aws_profile was not set.'
          ' Therefore, we will not be able to observe Amazon Web Services.')

  def __init_google_bindings(self):
    bindings = self.bindings  # base class made a copy
    if not bindings['TEST_GCE_ZONE']:
      bindings['TEST_GCE_ZONE'] = bindings['GCE_ZONE']
    if not bindings.get('TEST_GCE_REGION', ''):
      bindings['TEST_GCE_REGION'] = bindings['TEST_GCE_ZONE'][:-2]

    if bindings.get('GOOGLE_PRIMARY_MANAGED_PROJECT_ID'):
      self.__gcp_observer = gcp.GcpComputeAgent.make_agent(
          scopes=(gcp.COMPUTE_READ_WRITE_SCOPE
                  if bindings['GCE_CREDENTIALS_PATH'] else None),
          credentials_path=bindings['GCE_CREDENTIALS_PATH'],
          default_variables={
              'project': bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'],
              'region': bindings['TEST_GCE_REGION'],
              'zone':bindings['TEST_GCE_ZONE']
          })
    else:
      self.__gcp_observer = None
      logger = logging.getLogger(__name__)
      logger.warning(
          '--managed_gce_project was not set nor could it be inferred.'
          ' Therefore, we will not be able to observe Google Compute Engine.')

  def __init_kubernetes_bindings(self):
    bindings = self.bindings  # base class made a copy
    if bindings.get('SPINNAKER_KUBERNETES_ACCOUNT'):
      self.__kube_observer = kube.KubeCtlAgent()
    else:
      self.__kube_observer = None

  def __update_bindings_with_subsystem_configuration(self, agent):
    """Helper function for setting agent bindings from actual configuration.

    This uses the agent's runtime_config, if available, to supply some
    abstract binding information so that the test can adapt to the deployment
    it is testing.
    """
    # pylint: disable=bad-indentation

    # For read-only tests that don't make mutating calls to Spinnaker,
    # there is nothing to update in the bindings, e.g. GCP quota test.
    if agent is None:
      return
    for key, value in agent.runtime_config.items():
        try:
          if self.bindings[key]:
            continue
        except KeyError:
          pass
        self.bindings[key] = value

    if not self.bindings['SPINNAKER_GOOGLE_ACCOUNT']:
      self.bindings['SPINNAKER_GOOGLE_ACCOUNT'] = (
          self.agent.deployed_config.get(
              'providers.google.primaryCredentials.name', None))

    if not self.bindings['SPINNAKER_KUBERNETES_ACCOUNT']:
      self.bindings['SPINNAKER_KUBERNETES_ACCOUNT'] = (
          self.agent.deployed_config.get(
              'providers.kubernetes.primaryCredentials.name', None))

    if not self.bindings['SPINNAKER_AWS_ACCOUNT']:
      self.bindings['SPINNAKER_AWS_ACCOUNT'] = self.agent.deployed_config.get(
          'providers.aws.primaryCredentials.name', None)

    if not self.bindings['AWS_IAM_ROLE']:
      self.bindings['AWS_IAM_ROLE'] = self.agent.deployed_config.get(
          'providers.aws.defaultIAMRole', None)

    if not self.bindings.get('GOOGLE_PRIMARY_MANAGED_PROJECT_ID'):
      # Default to the project we are managing.
      self.bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'] = (
          self.agent.deployed_config.get(
              'providers.google.primaryCredentials.project', None))
      if not self.bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID']:
        # But if that wasnt defined then default to the subsystem's project.
        self.bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'] = (
            self.bindings['GCE_PROJECT'])
