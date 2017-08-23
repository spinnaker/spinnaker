# Copyright 2017 Cerner Corporation All Rights Reserved.
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
Smoke test to see if Spinnaker can interoperate with DC/OS.

See testable_service/integration_test.py and spinnaker_testing/spinnaker.py
for more details.

Sample Usage:
  These tests expect the 'dcos' utility to be available and configured for the DC/OS cluster being tested against.

  PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
    python $CITEST_ROOT/tests/dcos_smoke_test.py \
    --native_hostname=$HOSTNAME \
    --native_port=$PORT \
    --spinnaker_dcos_account=$ACCOUNT \
    --spinnaker_dcos_cluster=$SPINNAKER_DCOS_CLUSTER
"""

# Standard python modules.
import sys

# citest modules.
import citest.json_contract as jc
import citest.json_predicate as jp
import citest.service_testing as st
import citest_contrib.dcos_testing as dcos

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate
import spinnaker_testing.frigga as frigga
import citest.base


class DcosSmokeTestScenario(sk.SpinnakerTestScenario):
    """Defines the scenario for the smoke test.
  
    This scenario defines the different test operations.
    We're going to:
      Create a Spinnaker Application
      Create a Spinnaker Server Group
      Create a Pipeline with the following stages
        - Deploy
        - Resize
      Delete each of the above (in reverse order)
    """

    @classmethod
    def new_agent(cls, bindings):
        """Implements citest.service_testing.AgentTestScenario.new_agent."""
        agent = gate.new_agent(bindings)
        agent.default_max_wait_secs = 180
        return agent

    def __init__(self, bindings, agent=None):
        """Constructor.
    
        Args:
          bindings: [dict] The data bindings to use to configure the scenario.
          agent: [GateAgent] The agent for invoking the test operations on Gate.
        """
        super(DcosSmokeTestScenario, self).__init__(bindings, agent)
        bindings = self.bindings

        self.pipeline_id = None

        # No detail because name length is restricted too much to afford one!
        self.__lb_detail = ''
        self.__lb_name = frigga.Naming.cluster(
            app=bindings['TEST_APP'],
            stack=bindings['TEST_STACK'])

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
                account_name=self.bindings['SPINNAKER_DCOS_ACCOUNT']),
            contract=contract)

    def delete_app(self):
        """Creates OperationContract that deletes a new Spinnaker Application."""
        contract = jc.Contract()
        return st.OperationContract(
            self.agent.make_delete_app_operation(
                application=self.TEST_APP,
                account_name=self.bindings['SPINNAKER_DCOS_ACCOUNT']),
            contract=contract)

    def create_server_group(self):
        """Creates OperationContract for createServerGroup.
    
        To verify the operation, we just check that the server group was created.
        """
        bindings = self.bindings

        # Spinnaker determines the group name created,
        # which will be the following:
        group_name = frigga.Naming.server_group(
            app=self.TEST_APP,
            stack=bindings['TEST_STACK'],
            version='v000')

        payload = self.agent.make_json_payload_from_kwargs(
            job=[{
                'cloudProvider': 'dcos',
                'application': self.TEST_APP,
                'account': bindings['SPINNAKER_DCOS_ACCOUNT'],
                'env': {},
                'desiredCapacity': 1,
                'cpus': 0.1,
                'mem': 64,
                'docker': {
                    'image': {
                        'repository': 'nginx',
                        'tag': 'canary',
                        'imageId': 'nginx',
                        'registry': 'docker.io',
                        'account': bindings['SPINNAKER_DOCKER_ACCOUNT']
                    }
                },
                'networkType': 'BRIDGE',
                'stack': bindings['TEST_STACK'],
                'type': 'createServerGroup',
                'region': bindings['SPINNAKER_DCOS_CLUSTER'],
                'dcosCluster': bindings['SPINNAKER_DCOS_CLUSTER'],
                'user': '[anonymous]'
            }],
            description='Create Server Group in ' + group_name,
            application=self.TEST_APP)

        builder = dcos.DcosContractBuilder(self.dcos_observer)
        (builder.new_clause_builder('Marathon App Added', retryable_for_secs=240)
         .get_marathon_resources('app'.format(bindings['SPINNAKER_DCOS_ACCOUNT']))
         .contains_path_value('id',
                              '/{0}/{1}'.format(bindings['SPINNAKER_DCOS_ACCOUNT'], group_name)))

        return st.OperationContract(
            self.new_post_operation(
                title='create_server_group', data=payload, path='tasks'),
            contract=builder.build())

    def delete_server_group(self, version='v000'):
        """Creates OperationContract for deleteServerGroup.
    
        To verify the operation, we just check that the DC/OS application
        is no longer visible (or is in the process of terminating).
        """
        bindings = self.bindings
        group_name = frigga.Naming.server_group(
            app=self.TEST_APP, stack=bindings['TEST_STACK'], version=version)

        payload = self.agent.make_json_payload_from_kwargs(
            job=[{
                'cloudProvider': 'dcos',
                'type': 'destroyServerGroup',
                'account': bindings['SPINNAKER_DCOS_ACCOUNT'],
                'credentials': bindings['SPINNAKER_DCOS_ACCOUNT'],
                'user': '[anonymous]',
                'serverGroupName': group_name,
                'asgName': group_name,
                'regions': [bindings['SPINNAKER_DCOS_CLUSTER']],
                'region': bindings['SPINNAKER_DCOS_CLUSTER'],
                'dcosCluster': bindings['SPINNAKER_DCOS_CLUSTER'],
                'zones': [bindings['SPINNAKER_DCOS_CLUSTER']]
            }],
            application=self.TEST_APP,
            description='Destroy Server Group: ' + group_name)

        builder = dcos.DcosContractBuilder(self.dcos_observer)
        (builder.new_clause_builder('Marathon App Deleted', retryable_for_secs=240)
         .get_marathon_resources('app'.format(bindings['SPINNAKER_DCOS_ACCOUNT']))
         .excludes_path_value('id',
                              '/{0}/{1}'.format(bindings['SPINNAKER_DCOS_ACCOUNT'], group_name)))

        contract = jc.Contract()
        return st.OperationContract(
            self.new_post_operation(
                title='delete_server_group', data=payload, path='tasks'),
            contract=contract)

    def make_deploy_stage(self, requisiteStages=[], **kwargs):
        bindings = self.bindings
        result = {
            'requisiteStageRefIds': requisiteStages,
            'refId': 'DEPLOY',
            'type': 'deploy',
            'name': 'Deploy server group',
            'clusters': [
                {
                    'cloudProvider': 'dcos',
                    'application': self.TEST_APP,
                    'account': bindings['SPINNAKER_DCOS_ACCOUNT'],
                    'env': {},
                    'desiredCapacity': 1,
                    'cpus': 0.1,
                    'mem': 64,
                    'docker': {
                        'image': {
                            'repository': 'nginx',
                            'tag': 'canary',
                            'imageId': 'nginx',
                            'registry': 'docker.io',
                            'account': bindings['SPINNAKER_DOCKER_ACCOUNT']
                        }
                    },
                    'networkType': 'BRIDGE',
                    'stack': bindings['TEST_STACK'],
                    'region': bindings['SPINNAKER_DCOS_CLUSTER'],
                    'dcosCluster': bindings['SPINNAKER_DCOS_CLUSTER'],
                    'user': '[anonymous]'
                }
            ]
        }

        result.update(kwargs)
        return result

    def make_resize_stage(self, cluster, requisiteStages=[], **kwargs):
        bindings = self.bindings

        result = {
            'action': 'scale_exact',
            'capacity': {
                'desired': 3
            },
            'cloudProvider': 'dcos',
            'cloudProviderType': 'dcos',
            'cluster': cluster,
            'credentials': bindings['SPINNAKER_DCOS_ACCOUNT'],
            'name': 'Resize Server Group',
            'refId': 'RESIZE',
            'regions': [
                bindings['SPINNAKER_DCOS_CLUSTER']
            ],
            'requisiteStageRefIds': requisiteStages,
            'resizeType': 'exact',
            'target': 'current_asg_dynamic',
            'type': 'resizeServerGroup'
        }

        result.update(kwargs)
        return result

    def create_deploy_pipeline(self):
        name = 'deployServerPipeline'

        cluster = frigga.Naming.cluster(
            app=self.TEST_APP,
            stack=self.bindings['TEST_STACK'])

        self.pipeline_id = name
        deploy_stage = self.make_deploy_stage(requisiteStages=[])
        resize_stage = self.make_resize_stage(requisiteStages=['DEPLOY'], cluster=cluster)

        pipeline_spec = dict(
            name=name,
            stages=[deploy_stage, resize_stage],
            triggers=[],
            application=self.TEST_APP,
            stageCounter=2,
            parallel=True,
            limitConcurrent=True,
            executionEngine='v2',
            appConfig={},
            index=0
        )

        payload = self.agent.make_json_payload_from_kwargs(**pipeline_spec)

        builder = st.HttpContractBuilder(self.agent)
        (builder.new_clause_builder('Has Pipeline',
                                    retryable_for_secs=15)
         .get_url_path(
            'applications/{app}/pipelineConfigs'.format(app=self.TEST_APP))
         .contains_path_value(None, pipeline_spec))

        return st.OperationContract(
            self.new_post_operation(
                title='create_deploy_pipeline', data=payload, path='pipelines',
                status_class=st.SynchronousHttpOperationStatus),
            contract=builder.build())

    def run_deploy_pipeline(self):
        path = 'pipelines/{0}/{1}'.format(self.TEST_APP, self.pipeline_id)
        bindings = self.bindings
        group_name = frigga.Naming.server_group(
            app=self.TEST_APP,
            stack=bindings['TEST_STACK'],
            version='v001')

        payload = self.agent.make_json_payload_from_kwargs(
            type='manual',
            user='[anonymous]')

        builder = dcos.DcosContractBuilder(self.dcos_observer)
        (builder.new_clause_builder('Marathon App deployed via pipeline', retryable_for_secs=240)
         .get_marathon_resources('app'.format(bindings['SPINNAKER_DCOS_ACCOUNT']))
         .contains_path_value('id',
                              '/{0}/{1}'.format(bindings['SPINNAKER_DCOS_ACCOUNT'], group_name)))

        (builder.new_clause_builder('Marathon App has expected # instances', retryable_for_secs=240)
         .get_marathon_resources('app'.format(bindings['SPINNAKER_DCOS_ACCOUNT']))
         .contains_path_eq('instances', 3))

        return st.OperationContract(
            self.new_post_operation(
                title='run_deploy_pipeline', data=payload, path=path),
            builder.build())


class DcosSmokeTest(st.AgentTestCase):
    """The test fixture for the DcosSmokeTest.
  
    This is implemented using citest OperationContract instances that are
    created by the DcosSmokeTestScenario.
    """

    # pylint: disable=missing-docstring

    @property
    def scenario(self):
        return citest.base.TestRunner.global_runner().get_shared_data(
            DcosSmokeTestScenario)

    def test_a_create_app(self):
        self.run_test_case(self.scenario.create_app())

    def test_b_create_server_group(self):
        self.run_test_case(self.scenario.create_server_group(),
                           max_retries=1,
                           timeout_ok=True)

    def test_c_create_deploy_pipeline(self):
        self.run_test_case(self.scenario.create_deploy_pipeline())

    def test_d_run_deploy_pipeline(self):
        self.run_test_case(self.scenario.run_deploy_pipeline())

    def test_x_delete_server_group(self):
        self.run_test_case(self.scenario.delete_server_group('v000'), max_retries=2)

    def test_x2_delete_server_group(self):
        self.run_test_case(self.scenario.delete_server_group('v001'), max_retries=2)

    def test_z_delete_app(self):
        # Give a total of a minute because it might also need
        # an internal cache update
        self.run_test_case(self.scenario.delete_app(),
                           retry_interval_secs=8, max_retries=8)


def main():
    """Implements the main method running this smoke test."""

    defaults = {
        'TEST_STACK': 'tst',
        'TEST_APP': 'dcossmok' + DcosSmokeTestScenario.DEFAULT_TEST_ID
    }

    return citest.base.TestRunner.main(
        parser_inits=[DcosSmokeTestScenario.initArgumentParser],
        default_binding_overrides=defaults,
        test_case_list=[DcosSmokeTest])


if __name__ == '__main__':
    sys.exit(main())
