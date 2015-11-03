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
#
# Sample Usage:
#     Assuming you have created $PASSPHRASE_FILE (which you should chmod 400)
#     and $CITEST_ROOT points to the root directory of this repository
#     (which is . if you execute this from the root). The passphrase file
#     can be ommited if you run ssh-agent and add .ssh/compute_google_engine.
#
#   PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
#     python $CITEST_ROOT/spinnaker/spinnaker_system/bake_and_deploy_test.py \
#     --gce_ssh_passphrase_file=$PASSPHRASE_FILE \
#     --gce_project=$PROJECT \
#     --gce_zone=$ZONE \
#     --gce_instance=$INSTANCE
# or
#   PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
#     python $CITEST_ROOT/spinnaker/spinnaker_system/bake_and_deploy_test.py \
#     --native_hostname=host-running-smoke-test
#     --managed_gce_project=$PROJECT \
#     --test_gce_zone=$ZONE


# Standard python modules.
import os
import sys

# citest modules.
import citest.gcp_testing as gcp
import citest.json_contract as jc
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate


class BakeAndDeployTestScenario(sk.SpinnakerTestScenario):
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
        '--test_component_detail',
        default='fe',
        help='Refinement for component name to create.')

    parser.add_argument(
      '--jenkins_master', default='jenkins1',
      help='The name of the jenkins master as configured in igor.')
    parser.add_argument(
      '--jenkins_job', default='TestTriggerProject',
      help='The name of the jenkins job to trigger off.')

  def __init__(self, bindings, agent=None):
    super(BakeAndDeployTestScenario, self).__init__(bindings, agent)

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

  def create_app(self):
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_create_app_operation(
            bindings=self._bindings, application=self.TEST_APP),
        contract=contract)

  def delete_app(self):
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_delete_app_operation(
            bindings=self._bindings, application=self.TEST_APP),
        contract=contract)

  def create_load_balancer(self):
    bindings = self._bindings
    load_balancer_name = self.__short_lb_name

    spec = {
      'checkIntervalSec': 5,
      'healthyThreshold': 2,
      'unhealthyThreshold': 2,
      'timeoutSec': 5,
      'port': 80
    }

    payload = self.agent.make_payload(
      job=[{
          'cloudProvider': 'gce',
          'provider': 'gce',
          'providerType': 'gce',
          'stack': bindings['TEST_STACK'],
          'detail': self.__short_lb_name,
          'credentials': bindings['GCE_CREDENTIALS'],
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
          'type': 'upsertAmazonLoadBalancer',
          'availabilityZones': { bindings['TEST_GCE_REGION']: [] },
          'user': '[anonymous]'
      }],
      description='Create Load Balancer: ' + load_balancer_name,
      application=self.TEST_APP)

    # We arent testing load balancers, so assume it is working,
    # but we'll look for at the health check to know it is ready.
    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Health Check Added',
                                retryable_for_secs=30)
         .list_resources('http-health-checks')
         .contains('name', load_balancer_name + '-hc'))

    return st.OperationContract(
        self.new_post_operation(
            title='create_load_balancer', data=payload, path='tasks'),
        contract=builder.build())

  def delete_load_balancer(self):
    bindings = self._bindings
    payload = self.agent.make_payload(
       job=[{
          'type': 'deleteLoadBalancer',
          'cloudProvider': 'gce',
          'loadBalancerName': self.__full_lb_name,
          'region': bindings['TEST_GCE_REGION'],
          'regions': [bindings['TEST_GCE_REGION']],
          'credentials': bindings['GCE_CREDENTIALS'],
          'user': '[anonymous]'
       }],
       description='Delete Load Balancer: {0} in {1}:{2}'.format(
          self.__full_lb_name,
          bindings['GCE_CREDENTIALS'],
          bindings['TEST_GCE_REGION']),
      application=self.TEST_APP)

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Health Check Removed', retryable_for_secs=30)
         .list_resources('http-health-checks')
         .excludes('name', self.__full_lb_name + '-hc'))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_load_balancer', data=payload, path='tasks'),
        contract=builder.build())

  def make_jenkins_trigger(self):
    return {
      'enabled': True,
      'type': 'jenkins',
      'master': self.bindings['JENKINS_MASTER'],
      'job': self.bindings['JENKINS_JOB']
      }

  def make_bake_stage(self, package, providerType, **kwargs):
    result = {
        'requisiteStageRefIds':[],
        'refId': '1',
        'type': 'bake',
        'name': 'Bake',
        'user': '[anonymous]',
        'baseOs': 'trusty',
        'baseLabel': 'release',
        'cloudProviderType': providerType,
        'package': package
    }
    result.update(kwargs)
    return result

  def make_deploy_google_stage(self):
    return {
      'requisiteStageRefIds': ['1'],
      'refId': '2',
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
        'instanceMetadata': [{
          'startup-script':
            'sudo apt-get update && sudo apt-get install apache2 -y',
          'load-balancer-names': self.__full_lb_name
        }],
        'tags': [],
        'availabilityZones': {
          self.bindings['TEST_GCE_REGION']: [self.bindings['TEST_GCE_ZONE']]
        },

        'cloudProvider': 'gce',
        'providerType': 'gce',
        'provider': 'gce',
        'instanceType': 'f1-micro',
        'image': None,
        'account': self.bindings['GCE_CREDENTIALS']
      }]
    }

  def make_disable_group_stage(self, cloudProvider, **kwargs):
    result = {
      'requisiteStageRefIds': ['2'],
      'refId': '3',
      'type': 'disableServerGroup',
      'name': 'Disable Server Group',
      'cloudProviderType': cloudProvider,
      'cloudProvider': cloudProvider,
      'target': 'ancestor_asg_dynamic',
      'cluster': '{app}-{stack}'.format(
          app=self.TEST_APP, stack=self.bindings['TEST_STACK']),
      'credentials': self.bindings['GCE_CREDENTIALS']
    }
    result.update(kwargs)
    return result

  def create_bake_docker_pipeline(self):
    name = 'BakeDocker'
    self.docker_pipeline_id = name
    bake_stage = self.make_bake_stage(
        package='kato', providerType='docker', region='global')

    pipeline_spec = dict(
      name=name,
      stages=[bake_stage],
      triggers=[self.make_jenkins_trigger()],
      application=self.TEST_APP,
      stageCounter=1,
      parallel=True,
      index=0,
      limitConcurrent=True,
      appConfig={}
    )
    payload = self.agent.make_payload(**pipeline_spec)

    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline')
       .get_url_path(
           'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
       .contains(None, pipeline_spec))

    return st.OperationContract(
        self.new_post_operation(
            title='create_bake_pipeline', data=payload, path='pipelines',
            status_class=st.SynchronousHttpOperationStatus),
        contract=builder.build())

  def create_bake_and_deploy_google_pipeline(self):
    name = 'BakeAndDeployGoogle'
    self.google_pipeline_id = name
    bake_stage = self.make_bake_stage(
        package='kato', providerType='gce', region='global')
    deploy_stage = self.make_deploy_google_stage()
    disable_stage = self.make_disable_group_stage(
      cloudProvider='gce', zones=[self.bindings['TEST_GCE_ZONE']])

    pipeline_spec = dict(
      name=name,
      stages=[bake_stage,  deploy_stage, disable_stage],
      triggers=[self.make_jenkins_trigger()],
      application=self.TEST_APP,
      stageCounter=3,
      parallel=True,
      limitConcurrent=True,
      appConfig={},
      index=3
    )
    payload = self.agent.make_payload(**pipeline_spec)

    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline')
       .get_url_path(
           'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
       .contains(None, pipeline_spec))

    return st.OperationContract(
        self.new_post_operation(
            title='create_bake_pipeline', data=payload, path='pipelines',
            status_class=st.SynchronousHttpOperationStatus),
        contract=builder.build())

  def create_bake_and_deploy_aws_pipeline(self):
    name = 'BakeAndDeployAws'
    self.aws_pipeline_id = name
    bake_stage = self.make_bake_stage(
        package='kato',
        providerType='aws',
        regions=[self.bindings['TEST_AWS_REGION']],
        vmType='hvm', storeType='ebs')
    deploy_stage = self.make_deploy_google_stage()
    disable_stage = self.make_disable_group_stage(
      cloudProvider='aws', regions=[self.bindings['TEST_AWS_REGION']])

    pipeline_spec = dict(
      name=name,
      stages=[bake_stage, deploy_stage, disable_stage],
      triggers=[self.make_jenkins_trigger()],
      application=self.TEST_APP,
      stageCounter=2,
      parallel=True,
      index=1
    )
    payload = self.agent.make_payload(**pipeline_spec)

    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline')
       .get_url_path(
           'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
       .contains(None, pipeline_spec))

    return st.OperationContract(
        self.new_post_operation(
            title='create_bake_pipeline', data=payload, path='pipelines',
            status_class=st.SynchronousHttpOperationStatus),
        contract=builder.build())

  def delete_pipeline(self, pipeline_id):
    payload = self.agent.make_payload(id=pipeline_id)
    path = os.path.join('pipelines', self.TEST_APP, pipeline_id)

    builder = st.HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Pipeline')
       .get_url_path(
           'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
       .excludes('name', pipeline_id))

    return st.OperationContract(
        self.new_delete_operation(
            title='delete_bake_pipeline', data=payload, path=path,
            status_class=st.SynchronousHttpOperationStatus),
        contract=builder.build())


class BakeAndDeployTest(st.AgentTestCase):
  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

#  TODO(ewiseblatt):
#  Uncomment this when it is needed later (for executing pipelines)
#  def test_b_create_load_balancer(self):
#    self.run_test_case(self.scenario.create_load_balancer())

  def test_c1_create_bake_docker_pipeline(self):
    self.run_test_case(self.scenario.create_bake_docker_pipeline())

  def test_c2_create_bake_and_deploy_google_pipeline(self):
    self.run_test_case(self.scenario.create_bake_and_deploy_google_pipeline())

  def test_c3_create_bake_and_deploy_aws_pipeline(self):
    self.run_test_case(self.scenario.create_bake_and_deploy_aws_pipeline())

  def test_x1_delete_aws_pipeline(self):
    self.run_test_case(
        self.scenario.delete_pipeline(self.scenario.aws_pipeline_id))

  def test_x2_delete_docker_pipeline(self):
    self.run_test_case(
        self.scenario.delete_pipeline(self.scenario.docker_pipeline_id))

  def test_x3_delete_google_pipeline(self):
    self.run_test_case(
        self.scenario.delete_pipeline(self.scenario.google_pipeline_id))

#  TODO(ewiseblatt):
#  Uncomment this when it is needed later (for executing pipelines)
#  def test_y_delete_load_balancer(self):
#    self.run_test_case(self.scenario.delete_load_balancer(),
#                       max_retries=5)

  def test_z_delete_app(self):
    # Give a total of a minute because it might also need
    # an internal cache update
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  defaults = {
    'TEST_STACK': 'baketest' + BakeAndDeployTestScenario.DEFAULT_TEST_ID,
    'TEST_APP': 'baketest' + BakeAndDeployTestScenario.DEFAULT_TEST_ID
  }

  return st.ScenarioTestRunner.main(
      BakeAndDeployTestScenario,
      default_binding_overrides=defaults,
      test_case_list=[BakeAndDeployTest])


if __name__ == '__main__':
  sys.exit(main())
