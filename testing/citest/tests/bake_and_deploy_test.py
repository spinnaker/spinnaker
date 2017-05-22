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


# See testable_service/integration_test.py and spinnaker_testing/spinnaker.py
# for more details.
#
# This test will use ssh to peek at the spinnaker configuration
# to determine the managed project it should verify, and to determine
# the spinnaker account name to use when sending it commands.
# Sample Usage:
#     Assuming you have created $PASSPHRASE_FILE (which you should chmod 400)
#     and $CITEST_ROOT points to the root directory of this repository
#     (which is . if you execute this from the root). The passphrase file
#     can be ommited if you run ssh-agent and add .ssh/compute_google_engine.
#
#     Since this test runs a pipeline from a Jenkins trigger, you need to
#     configure Jenkins in the following way.
#         1. Take note of your Jenkins server baseUrl,
#            i.e <protocol>://<host>[:port]/[basePath]
#            and store it as $JENKINS_URL.
#
#         2. Create a file, fill it with
#            <username> <password>
#            corresponding to valid Jenkins credentials, and store its path
#            as $JENKINS_AUTH_PATH (also chmod 400).
#            Or, set JENKINS_USER and JENKINS_PASSWORD environment variables.
#
#         3. Take note of the Jenkins master you have configured in Igor,
#            and store its name as $JENKINS_MASTER.
#
#         4. Choose a name for your jenkins job and store it in $JENKINS_JOB.
#
#         5. On your Jenkins server, navigate to /job/$JENKINS_JOB/configure
#               a) Under "Build Triggers", check "Trigger builds remotely".
#               b) In the "Authentication Token" field, write some token
#                  and store it as $JENKINS_TOKEN.
#               c) Add a build step that produces a file.
#                  mkdir -p somedir
#                  touch somedir/vim_2:7.4.052-1ubuntu3_amd64.deb
#                  Note that this might need to be consistent with the
#                  platform the bakery is on. The above works on Ubuntu 14.04
#               d) Add post build action to archive the artifacts
#                  files to archive: somedir/vim_2:7.4.052-1ubuntu3_amd64.deb
#
#
#   PYTHONPATH=$CITEST_ROOT/testing/citest \
#     python $CITEST_ROOT/testing/citest/tests/bake_and_deploy_test.py \
#     --gce_ssh_passphrase_file=$PASSPHRASE_FILE \
#     --gce_project=$PROJECT \
#     --gce_zone=$ZONE \
#     --gce_instance=$INSTANCE \
#     --jenkins_master=$JENKINS_MASTER \
#     --jenkins_url=$JENKINS_URL \
#     --jenkins_auth_path=$JENKINS_AUTH_PATH \
#     --jenkins_job=$JENKINS_JOB \
#     --jenkins_token=$JENKINS_TOKEN \
#     --test_google \
#     --test_aws
# or
#   PYTHONPATH=$CITEST_ROOT/testing/citest \
#     python $CITEST_ROOT/testing/citest/tests/bake_and_deploy_test.py \
#     --native_hostname=host-running-smoke-test
#     --managed_gce_project=$PROJECT \
#     --test_gce_zone=$ZONE
#     --jenkins_url=$JENKINS_URL \
#     --jenkins_auth_path=$JENKINS_AUTH_PATH \
#     --jenkins_job=$JENKINS_JOB \
#     --jenkins_token=$JENKINS_TOKEN
#     --test_google \
#     --test_aws

# pylint: disable=bad-continuation
# pylint: disable=invalid-name
# pylint: disable=missing-docstring

# Standard python modules.
import os
import sys
import logging

# citest modules.
import citest.base
import citest.gcp_testing as gcp
import citest.json_contract as jc
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate


class BakeAndDeployTestScenario(sk.SpinnakerTestScenario):

  MINIMUM_PROJECT_QUOTA = {
      'INSTANCE_TEMPLATES': 1,
      'HEALTH_CHECKS': 1,
      'FORWARDING_RULES': 1,
      'IN_USE_ADDRESSES': 1,
      'TARGET_POOLS': 1,
      'IMAGES': 1,
  }

  MINIMUM_REGION_QUOTA = {
      'CPUS': 1,
      'IN_USE_ADDRESSES': 1,
      'INSTANCE_GROUP_MANAGERS': 1,
      'INSTANCES': 1,
  }

  @classmethod
  def new_agent(cls, bindings):
    return gate.new_agent(bindings)

  @classmethod
  def initArgumentParser(cls, parser, defaults=None):
    """Initialize command line argument parser.

    Args:
      parser: argparse.ArgumentParser
    """
    super(BakeAndDeployTestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

    defaults = defaults or {}
    parser.add_argument(
      '--jenkins_master', default='',
      help='The name of the jenkins master as configured in igor.'
           ' You may need to override this to an alias depending on firewalls.'
           ' The Spinnaker server may have permissions, but the citest machine'
           ' may not. Otherwise, this defaults to Spinnaker\'s binding.')
    parser.add_argument(
      '--jenkins_job', default='NoOpTrigger',
      help='The name of the jenkins job to trigger off.'
           ' You will need to add this to your --jenkins_master.')
    parser.add_argument(
      '--jenkins_auth_path', default=None,
      help='The path to a file containing the jenkins username password pair.'
           'The contents should look like: <username> <password>.')
    parser.add_argument(
      '--jenkins_token', default='TRIGGER_TOKEN',
      help='The authentication token for the jenkins build trigger.'
      ' This corresponds to the --jenkins_job on the --jenkins_url server')

    parser.add_argument(
      '--jenkins_url', default='',
      help='The baseUrl of the jenkins service,'
           ' i.e. <protocol>://<host>[:port]/[basePath].'
           ' You may need to override this to an alias depending on firewalls.'
           ' The Spinnaker server may have permissions, but the citest machine'
           ' may not. Otherwise, this can be empty for Spinnaker\'s current'
           ' binding.')
    parser.add_argument(
      '--test_google', action='store_true',
      help='Test Google pipelines.')
    parser.add_argument(
      '--test_aws', action='store_true',
      help='Test AWS pipelines.')

  def _do_init_bindings(self):
    logger = logging.getLogger(__name__)
    bindings = self.bindings
    deployed = self.agent.deployed_config
    yaml_node_path = 'services.jenkins.defaultMaster'
    if not bindings.get('JENKINS_MASTER'):
      bindings['JENKINS_MASTER'] = deployed[yaml_node_path + '.name']
      logger.info('Infering JENKINS_MASTER %s', bindings['JENKINS_MASTER'])

    if not bindings.get('JENKINS_URL'):
      bindings['JENKINS_URL'] = deployed[yaml_node_path + '.baseUrl']
      logger.info('Infering JENKINS_URL %s', bindings['JENKINS_URL'])

  def __init__(self, bindings, agent=None):
    super(BakeAndDeployTestScenario, self).__init__(bindings, agent)
    self.logger = logging.getLogger(__name__)

    bindings = self.bindings

    # We'll call out the app name because it is widely used
    # because it scopes the context of our activities.
    self.TEST_APP = bindings['TEST_APP']

    self.__short_lb_name = 'lb'
    self.__full_lb_name = '{app}-{stack}-{detail}'.format(
            app=self.TEST_APP, stack=bindings['TEST_STACK'],
            detail=self.__short_lb_name)
    self.aws_pipeline_id = None
    self.google_pipeline_id = None
    self.docker_pipeline_id = None
    self.test_google = bindings['TEST_GOOGLE']
    self.test_aws = bindings['TEST_AWS']
    self.jenkins_agent = sk.JenkinsAgent(bindings['JENKINS_URL'],
        bindings['JENKINS_AUTH_PATH'], self.agent)
    self.run_tests = True

    if not (self.test_google or self.test_aws):
      self.run_tests = False
      self.logger.warning(
          'Neither --test_google nor --test_aws were set. '
          'No tests will be run.')

  def create_app(self):
    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Application', retryable_for_secs=60)
       .get_url_path('applications')
       .contains_path_value('name', self.TEST_APP))

    return st.OperationContract(
        self.agent.make_create_app_operation(
            bindings=self.bindings, application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_GOOGLE_ACCOUNT']),
        builder.build())

  def delete_app(self):
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_delete_app_operation(
            application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_GOOGLE_ACCOUNT']),
        contract=contract)

  def create_load_balancer(self):
    bindings = self.bindings
    load_balancer_name = self.__full_lb_name

    spec = {
      'checkIntervalSec': 5,
      'healthyThreshold': 2,
      'unhealthyThreshold': 2,
      'timeoutSec': 5,
      'port': 80
    }

    payload = self.agent.make_json_payload_from_kwargs(
      job=[{
          'cloudProvider': 'gce',
          'provider': 'gce',
          'stack': bindings['TEST_STACK'],
          'detail': self.__short_lb_name,
          'credentials': bindings['SPINNAKER_GOOGLE_ACCOUNT'],
          'region': bindings['TEST_GCE_REGION'],
          'ipProtocol': 'TCP',
          'portRange': spec['port'],
          'loadBalancerName': load_balancer_name,
          'healthCheck': {
              'port': spec['port'],
              'timeoutSec': spec['timeoutSec'],
              'checkIntervalSec': spec['checkIntervalSec'],
              'healthyThreshold': spec['healthyThreshold'],
              'unhealthyThreshold': spec['unhealthyThreshold'],
          },
          'type': 'upsertLoadBalancer',
          'availabilityZones': {bindings['TEST_GCE_REGION']: []},
          'user': '[anonymous]'
      }],
      description='Create Load Balancer: ' + load_balancer_name,
      application=self.TEST_APP)

    # We arent testing load balancers, so assume it is working,
    # but we'll look for at the health check to know it is ready.
    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Health Check Added',
                                retryable_for_secs=30)
         .list_resource('httpHealthChecks')
         .contains_path_value('name', load_balancer_name + '-hc'))

    (builder.new_clause_builder('Load Balancer Created',
                                retryable_for_secs=60)
         .list_resource('forwardingRules')
         .contains_path_value('name', self.__full_lb_name))

    return st.OperationContract(
        self.new_post_operation(
            title='create_load_balancer', data=payload,
            path=('applications/{app}/tasks').format(app=self.TEST_APP)),
        contract=builder.build())

  def delete_load_balancer(self):
    bindings = self.bindings
    payload = self.agent.make_json_payload_from_kwargs(
       job=[{
          'type': 'deleteLoadBalancer',
          'cloudProvider': 'gce',
          'loadBalancerName': self.__full_lb_name,
          'region': bindings['TEST_GCE_REGION'],
          'regions': [bindings['TEST_GCE_REGION']],
          'credentials': bindings['SPINNAKER_GOOGLE_ACCOUNT'],
          'user': '[anonymous]'
       }],
       description='Delete Load Balancer: {0} in {1}:{2}'.format(
          self.__full_lb_name,
          bindings['SPINNAKER_GOOGLE_ACCOUNT'],
          bindings['TEST_GCE_REGION']),
      application=self.TEST_APP)

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Health Check Removed', retryable_for_secs=30)
         .list_resource('httpHealthChecks')
         .excludes_path_value('name', self.__full_lb_name + '-hc'))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_load_balancer', data=payload,
            path=('applications/{app}/tasks').format(app=self.TEST_APP)),
        contract=builder.build())

  def make_jenkins_trigger(self):
    return {
      'enabled': True,
      'type': 'jenkins',
      'master': self.bindings['JENKINS_MASTER'],
      'job': self.bindings['JENKINS_JOB']
      }

  def make_bake_stage(self, package, providerType, requisiteStages=None,
      **kwargs):
    result = {
        'requisiteStageRefIds':requisiteStages or [],
        'refId': 'BAKE',
        'type': 'bake',
        'name': 'Bake',
        'user': '[anonymous]',
        'baseOs': 'trusty',
        'baseLabel': 'release',
        'cloudProviderType': providerType,
        'package': package,
        'rebake': True
    }
    result.update(kwargs)
    return result

  def make_deploy_google_stage(self, requisiteStages=None):
    return {
      'requisiteStageRefIds': requisiteStages or [],
      'refId': 'DEPLOY',
      'type': 'deploy',
      'name': 'Deploy',
      'clusters':[{
        'application': self.TEST_APP,
        'strategy': '',
        'stack': self.bindings['TEST_STACK'],
        'freeFormDetails': '',
        'loadBalancers': [self.__full_lb_name],
        'securityGroups': [],
        'capacity': {
          'min':1,
          'max':1,
          'desired':1
        },
        'zone': self.bindings['TEST_GCE_ZONE'],
        'network': 'default',
        'instanceMetadata': {
          'startup-script':
            'sudo apt-get update && sudo apt-get install apache2 -y',
          'load-balancer-names': self.__full_lb_name
        },
        'tags': [],
        'availabilityZones': {
          self.bindings['TEST_GCE_REGION']: [self.bindings['TEST_GCE_ZONE']]
        },

        'cloudProvider': 'gce',
        'provider': 'gce',
        'instanceType': 'f1-micro',
        'image': None,
        'targetSize': 1,
        'account': self.bindings['SPINNAKER_GOOGLE_ACCOUNT']
      }]
    }

  def make_destroy_group_stage(self, cloudProvider, requisiteStages,
      **kwargs):
    result = {
      'cloudProvider': cloudProvider,
      'cloudProviderType': cloudProvider,
      'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      'name': 'Destroy Server Group',
      'refId': 'DESTROY',
      'requisiteStageRefIds': requisiteStages or [],
      'target': 'current_asg_dynamic',
      'regions': [self.bindings['TEST_GCE_REGION']],
      'cluster': '{app}-{stack}'.format(
          app=self.TEST_APP, stack=self.bindings['TEST_STACK']),
      'type': 'destroyServerGroup'
    }
    result.update(kwargs)
    return result

  def make_disable_group_stage(self, cloudProvider, requisiteStages=None,
      **kwargs):
    result = {
      'requisiteStageRefIds': requisiteStages or [],
      'refId': 'DISABLE',
      'type': 'disableServerGroup',
      'name': 'Disable Server Group',
      'cloudProviderType': cloudProvider,
      'cloudProvider': cloudProvider,
      'target': 'current_asg_dynamic',
      'cluster': '{app}-{stack}'.format(
          app=self.TEST_APP, stack=self.bindings['TEST_STACK']),
      'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT']
    }
    result.update(kwargs)
    return result

  def create_bake_docker_pipeline(self):
    name = 'BakeDocker'
    self.docker_pipeline_id = name
    bake_stage = self.make_bake_stage(
        package='vim', providerType='docker', region='global')

    pipeline_spec = dict(
      name=name,
      stages=[bake_stage],
      triggers=[self.make_jenkins_trigger()],
      application=self.TEST_APP,
      stageCounter=1,
      parallel=True,
      index=0,
      limitConcurrent=True,
      executionEngine='v2',
      appConfig={}
    )
    payload = self.agent.make_json_payload_from_kwargs(**pipeline_spec)

    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline')
       .get_url_path(
           'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
       .contains_path_value(None, pipeline_spec))

    return st.OperationContract(
        self.new_post_operation(
            title='create_bake_docker_pipeline', data=payload, path='pipelines',
            status_class=st.SynchronousHttpOperationStatus),
        contract=builder.build())

  def create_bake_and_deploy_google_pipeline(self):
    name = 'BakeAndDeployGoogle'
    self.google_pipeline_id = name
    bake_stage = self.make_bake_stage(
        package='vim', providerType='gce', region='global')
    deploy_stage = self.make_deploy_google_stage(requisiteStages=['BAKE'])
    disable_stage = self.make_disable_group_stage(
      cloudProvider='gce', regions=[self.bindings['TEST_GCE_REGION']],
      requisiteStages=['DEPLOY'])
    destroy_stage = self.make_destroy_group_stage(
      cloudProvider='gce', requisiteStages=['DISABLE'])

    pipeline_spec = dict(
      name=name,
      stages=[bake_stage, deploy_stage, disable_stage, destroy_stage],
      triggers=[self.make_jenkins_trigger()],
      application=self.TEST_APP,
      stageCounter=4,
      parallel=True,
      limitConcurrent=True,
      executionEngine='v2',
      appConfig={},
      index=3
    )

    payload = self.agent.make_json_payload_from_kwargs(**pipeline_spec)

    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline')
       .get_url_path(
           'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
       .contains_path_value(None, pipeline_spec))

    return st.OperationContract(
        self.new_post_operation(
            title='create_bake_google_pipeline', data=payload, path='pipelines',
            status_class=st.SynchronousHttpOperationStatus),
        contract=builder.build())

  def create_bake_and_deploy_aws_pipeline(self):
    name = 'BakeAndDeployAws'
    self.aws_pipeline_id = name
    bake_stage = self.make_bake_stage(
        package='vim',
        providerType='aws',
        regions=[self.bindings['TEST_AWS_REGION']],
        vmType='hvm', storeType='ebs')
    # FIXME(jacobkiefer): this is creating a gce deploy stage in an aws
    # pipeline. Not good.
    deploy_stage = self.make_deploy_google_stage(requisiteStages=['BAKE'])
    disable_stage = self.make_disable_group_stage(
      cloudProvider='aws', regions=[self.bindings['TEST_AWS_REGION']],
      requisiteStages=['DEPLOY'])
    destroy_stage = self.make_destroy_group_stage(
      cloudProvider='aws', zones=[self.bindings['TEST_AWS_ZONE']],
      requisiteStages=['DISABLE'])

    pipeline_spec = dict(
      name=name,
      stages=[bake_stage, deploy_stage, disable_stage, destroy_stage],
      triggers=[self.make_jenkins_trigger()],
      application=self.TEST_APP,
      stageCounter=4,
      parallel=True,
      index=1
    )
    payload = self.agent.make_json_payload_from_kwargs(**pipeline_spec)

    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline')
       .get_url_path(
           'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
       .contains_path_value(None, pipeline_spec))

    return st.OperationContract(
        self.new_post_operation(
            title='create_bake_aws_pipeline', data=payload, path='pipelines',
            status_class=st.SynchronousHttpOperationStatus),
        contract=builder.build())

  def delete_pipeline(self, pipeline_id):
    payload = self.agent.make_json_payload_from_kwargs(id=pipeline_id)
    path = os.path.join('pipelines', self.TEST_APP, pipeline_id)

    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline',
                                retryable_for_secs=5)
       .get_url_path(
           'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
       .excludes_path_value('name', pipeline_id))

    return st.OperationContract(
        self.new_delete_operation(
            title='delete_bake_pipeline', data=payload, path=path,
            status_class=st.SynchronousHttpOperationStatus),
        contract=builder.build())

  def run_bake_and_deploy_google_pipeline(self, pipeline_id):
    path = 'applications/{app}/pipelines'.format(app=self.TEST_APP)

    return st.OperationContract(
        self.jenkins_agent.new_jenkins_trigger_operation(
            title='monitor_bake_pipeline',
            job=self.bindings['JENKINS_JOB'],
            token=self.bindings['JENKINS_TOKEN'],
            status_class=gate.GatePipelineStatus,
            status_path=path),
        contract=jc.Contract(),
        cleanup=self.delete_baked_image)

  def new_jenkins_build_operation(self):
    return None

  def delete_baked_image(self, execution_context):
    status = execution_context.get('OperationStatus', None)
    if status is None:
      self.logger.info(
          'Operation could not be performed so there is no image to delete.')
      return;

    status = status.trigger_status
    detail = status.detail_doc
    if isinstance(detail, list):
      if not detail:
        self.logger.error('No trigger_status, so baked image is unknown\n'
                          '%s\n\n', status)
        return
      self.logger.info('Using first status.')
      detail = detail[0]

    context = detail.get('context')
    details = context.get('deploymentDetails') if context else None
    name = details[0].get('imageId') if details else None
    self.logger.info('Deleting the baked image="%s"', name)
    if name:
      execution_context = citest.base.ExecutionContext()
      self.gcp_observer.invoke_resource(
          execution_context, 'delete', 'images', resource_id=name)


class BakeAndDeployTest(st.AgentTestCase):
  @staticmethod
  def setUpClass():
    runner = citest.base.TestRunner.global_runner()
    scenario = runner.get_shared_data(BakeAndDeployTestScenario)
    if not scenario.test_google:
      return

    managed_region = scenario.bindings['TEST_GCE_REGION']
    title = 'Check Quota for {0}'.format(scenario.__class__.__name__)

    verify_results = gcp.verify_quota(
        title,
        scenario.gcp_observer,
        project_quota=BakeAndDeployTestScenario.MINIMUM_PROJECT_QUOTA,
        regions=[(managed_region,
                  BakeAndDeployTestScenario.MINIMUM_REGION_QUOTA)])
    if not verify_results:
      raise RuntimeError('Insufficient Quota: {0}'.format(verify_results))

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        BakeAndDeployTestScenario)

  @property
  def testing_agent(self):
    return self.scenario.agent

  def test_a_create_app(self):
    if not self.scenario.run_tests:
      self.skipTest("No --test_{google, aws} flags were set")
    else:
      self.run_test_case(self.scenario.create_app())

  def test_b_create_load_balancer(self):
    if not self.scenario.run_tests:
      self.skipTest("No --test_{google, aws} flags were set")
    else:
      self.run_test_case(self.scenario.create_load_balancer())

  def test_c1_create_bake_and_deploy_google_pipeline(self):
    if not self.scenario.test_google:
      self.skipTest("--test_google flag not set")
    else:
      self.run_test_case(self.scenario.create_bake_and_deploy_google_pipeline(),
                       full_trace=True)

  def test_c2_create_bake_and_deploy_aws_pipeline(self):
    if not self.scenario.test_aws:
      self.skipTest("--test_aws flag not set")
    else:
      self.run_test_case(self.scenario.create_bake_and_deploy_aws_pipeline())

  def test_d1_run_bake_and_deploy_google_pipeline(self):
    if not self.scenario.test_google:
      self.skipTest("--test_google flag not set")
    else:
      self.run_test_case(self.scenario.run_bake_and_deploy_google_pipeline(
        self.scenario.google_pipeline_id))

  def test_x1_delete_google_pipeline(self):
    if not self.scenario.test_google:
      self.skipTest("--test_google flag not set")
    else:
      self.run_test_case(
        self.scenario.delete_pipeline(self.scenario.google_pipeline_id))

  def test_x2_delete_aws_pipeline(self):
    if not self.scenario.test_aws:
      self.skipTest("--test_aws flag not set")
    else:
      self.run_test_case(
        self.scenario.delete_pipeline(self.scenario.aws_pipeline_id))

  def test_y_delete_load_balancer(self):
    if not self.scenario.run_tests:
      self.skipTest("No --test_{google, aws} flags were set")
    else:
      self.run_test_case(self.scenario.delete_load_balancer(),
                       max_retries=5)

  def test_z_delete_app(self):
    if not self.scenario.run_tests:
      self.skipTest("No --test_{google, aws} flags were set")
    # Give a total of a minute because it might also need
    # an internal cache update
    else:
      self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  defaults = {
    'TEST_STACK': 'baketest' + BakeAndDeployTestScenario.DEFAULT_TEST_ID,
    'TEST_APP': 'baketest' + BakeAndDeployTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[BakeAndDeployTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[BakeAndDeployTest])


if __name__ == '__main__':
  sys.exit(main())
