# Copyright 2018 Google Inc. All Rights Reserved.
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
Integration test to see if the image promotion process is working for the
Spinnaker Kubernetes V2 integration.
"""

# Standard python modules.
import sys
import random
import string
import json

# citest modules.
from citest.service_testing import HttpContractBuilder
from citest.service_testing import NoOpOperation
import citest.kube_testing as kube
import citest.json_contract as jc
import citest.json_predicate as jp
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate
import spinnaker_testing.frigga as frigga
import citest.base

ov_factory = jc.ObservationPredicateFactory()

class KubeV2CacheTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the kube v2 cache test.
  """

  @classmethod
  def new_agent(cls, bindings):
    """Implements citest.service_testing.AgentTestScenario.new_agent."""
    agent = gate.new_agent(bindings)
    agent.default_max_wait_secs = 180
    return agent

  @classmethod
  def initArgumentParser(cls, parser, defaults=None):
    """Initialize command line argument parser.

    Args:
      parser: argparse.ArgumentParser
    """
    super(KubeV2CacheTestScenario, cls).initArgumentParser(
        parser, defaults=defaults)

    defaults = defaults or {}
    parser.add_argument(
      '--test_namespace', default='default',
      help='The namespace to manage within the tests.')

  def __init__(self, bindings, agent=None):
    """Constructor.

    Args:
      bindings: [dict] The data bindings to use to configure the scenario.
      agent: [GateAgent] The agent for invoking the test operations on Gate.
    """
    super(KubeV2CacheTestScenario, self).__init__(bindings, agent)
    bindings = self.bindings

    # We'll call out the app name because it is widely used
    # because it scopes the context of our activities.
    # pylint: disable=invalid-name
    self.TEST_APP = bindings['TEST_APP']

    # Take just the first if there are multiple
    # because some uses below assume just one.
    self.TEST_NAMESPACE = bindings['TEST_NAMESPACE'].split(',')[0]

    self.mf = sk.KubernetesManifestFactory(self)
    self.mp = sk.KubernetesManifestPredicateFactory()

  def create_app(self):
    """Creates OperationContract that creates a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_create_app_operation(
            bindings=self.bindings, application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']),
        contract=contract)

  def delete_app(self):
    """Creates OperationContract that deletes a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_delete_app_operation(
            application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']),
        contract=contract)

  def deploy_deployment(self, image):
    """Creates OperationContract for deploying and substituting one image into
    a Deployment object

    To verify the operation, we just check that the deployment was created with
    the correct image.
    """
    name = self.TEST_APP + '-deployment'
    account = self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'moniker': {
                'app': self.TEST_APP
            },
            'account': account,
            'source': 'text',
            'type': 'deployManifest',
            'user': '[anonymous]',
            'manifests': [self.mf.deployment(name, image)],
        }],
        description='Deploy manifest',
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Deployment created',
                                retryable_for_secs=15)
     .get_resources(
         'deploy',
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.deployment_image_predicate(image)))

    return st.OperationContract(
        self.new_post_operation(
            title='deploy_manifest', data=payload, path='tasks'),
        contract=builder.build())

  def deploy_service(self):
    """Creates an OperationContract for deploying a service object

    To verify the operation, we just check that the service was created.
    """
    name = self.TEST_APP + '-service'
    account = self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'moniker': {
                'app': self.TEST_APP
            },
            'account': account,
            'source': 'text',
            'type': 'deployManifest',
            'user': '[anonymous]',
            'manifests': [self.mf.service(name)],
        }],
        description='Deploy manifest',
        application=self.TEST_APP)

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Service created',
                                retryable_for_secs=15)
     .get_resources(
         'service',
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.service_selector_predicate('app', self.TEST_APP)))

    return st.OperationContract(
        self.new_post_operation(
            title='deploy_manifest', data=payload, path='tasks'),
        contract=builder.build())

  def check_manifest_endpoint_exists(self, kind):
    name = self.TEST_APP + '-' + kind
    account = self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']
    builder = HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has recorded a manifest')
       .get_url_path('/manifests/{account}/{namespace}/{name}'.format(
           account=account,
           namespace=self.TEST_NAMESPACE,
           name='{}%20{}'.format(kind, name)
       ))
       .EXPECT(
           ov_factory.value_list_contains(jp.DICT_MATCHES({
               'account': jp.STR_EQ(account)
           }))
    ))

    return st.OperationContract(
        NoOpOperation('Has recorded a manifest'),
        contract=builder.build())

  def check_applications_endpoint(self):
    account = self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']
    builder = HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has recorded an app for the deployed manifest')
       .get_url_path('/applications')
       .EXPECT(
           ov_factory.value_list_contains(jp.DICT_MATCHES({
               'name': jp.STR_EQ(self.TEST_APP),
               'accounts': jp.STR_SUBSTR(account),
           }))
    ))

    return st.OperationContract(
        NoOpOperation('Has recorded an application'),
        contract=builder.build())

  def check_server_groups_endpoint(self, kind, image, has_lb=True):
    name = self.TEST_APP + '-' + kind
    account = self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']
    builder = HttpContractBuilder(self.agent)
    lb_pred = (
        jp.LIST_MATCHES([jp.STR_EQ('service {}-service'.format(self.TEST_APP))])
        if has_lb else jp.LIST_EQ([])
    )
    (builder.new_clause_builder('Has recorded a server group for the deployed manifest')
       .get_url_path('/applications/{}/serverGroups'.format(self.TEST_APP))
       .EXPECT(
           ov_factory.value_list_contains(jp.DICT_MATCHES({
               'name': jp.STR_SUBSTR(name),
               'cluster': jp.STR_EQ(kind + ' ' + name),
               'account': jp.STR_EQ(account),
               'cloudProvider': jp.STR_EQ('kubernetes'),
               'buildInfo': jp.DICT_MATCHES({
                   'images': jp.LIST_MATCHES([jp.STR_EQ(image)]),
               }),
               'loadBalancers': lb_pred,
           }))
    ))

    return st.OperationContract(
        NoOpOperation('Has recorded a server group'),
        contract=builder.build())

  def check_load_balancers_endpoint(self, kind):
    name = kind + ' ' + self.TEST_APP + '-' + kind
    account = self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']
    builder = HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has recorded a load balancer')
       .get_url_path('/applications/{}/loadBalancers'.format(self.TEST_APP))
       .EXPECT(
           ov_factory.value_list_contains(jp.DICT_MATCHES({
               'name': jp.STR_EQ(name),
               'kind': jp.STR_EQ(kind),
               'account': jp.STR_EQ(account),
               'cloudProvider': jp.STR_EQ('kubernetes'),
               'serverGroups': jp.LIST_MATCHES([
                 jp.DICT_MATCHES({
                   'account': jp.STR_EQ(account),
                   'name': jp.STR_SUBSTR(self.TEST_APP),
                 }),
               ]),
           }))
    ))

    return st.OperationContract(
        NoOpOperation('Has recorded a load balancer'),
        contract=builder.build())

  def check_clusters_endpoint(self, kind):
    name = kind + ' ' + self.TEST_APP + '-' + kind
    account = self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']
    builder = HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has recorded a cluster for the deployed manifest')
       .get_url_path('/applications/{}/clusters'.format(self.TEST_APP))
       .EXPECT(
           ov_factory.value_list_contains(jp.DICT_MATCHES({
               account: jp.LIST_MATCHES([jp.STR_EQ(name)]),
           }))
    ))

    return st.OperationContract(
        NoOpOperation('Has recorded a cluster'),
        contract=builder.build())

  def check_detailed_clusters_endpoint(self, kind):
    name = kind + ' ' + self.TEST_APP + '-' + kind
    url_name = name.replace(' ', '%20')
    account = self.bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT']
    builder = HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has recorded a cluster for the deployed manifest')
       .get_url_path('/applications/{app}/clusters/{account}/{name}'.format(
         app=self.TEST_APP, account=account, name=url_name))
       .EXPECT(
           ov_factory.value_list_contains(jp.DICT_MATCHES({
               'accountName': jp.STR_EQ(account),
               'name': jp.STR_EQ(name),
               'serverGroups': jp.LIST_MATCHES([
                 jp.DICT_MATCHES({
                   'account': jp.STR_EQ(account),
                 })
               ]),
           }))
    ))

    return st.OperationContract(
        NoOpOperation('Has recorded a cluster'),
        contract=builder.build())

  def delete_kind(self, kind, version=None):
    """Creates OperationContract for deleteManifest

    To verify the operation, we just check that the Kubernetes deployment
    is no longer visible (or is in the process of terminating).
    """
    bindings = self.bindings
    name = self.TEST_APP + '-' + kind
    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            'cloudProvider': 'kubernetes',
            'type': 'deleteManifest',
            'account': bindings['SPINNAKER_KUBERNETES_V2_ACCOUNT'],
            'user': '[anonymous]',
            'kinds': [ kind ],
            'location': self.TEST_NAMESPACE,
            'options': { },
            'labelSelectors': {
                'selectors': [{
                    'kind': 'EQUALS',
                    'key': 'app',
                    'values': [ self.TEST_APP ]
                }]
            }
        }],
        application=self.TEST_APP,
        description='Destroy Manifest')

    if version is not None:
      name = name + '-' + version

    builder = kube.KubeContractBuilder(self.kube_v2_observer)
    (builder.new_clause_builder('Manifest Removed')
     .get_resources(
         kind,
         extra_args=[name, '--namespace', self.TEST_NAMESPACE])
     .EXPECT(self.mp.not_found_observation_predicate()))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_kind', data=payload, path='tasks'),
        contract=builder.build())

class KubeV2CacheTest(st.AgentTestCase):
  """The test fixture for the KubeV2CacheTest.

  This is implemented using citest OperationContract instances that are
  created by the KubeV2CacheTestScenario.
  """
  # pylint: disable=missing-docstring

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        KubeV2CacheTestScenario)

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  def test_b1_deploy_deployment(self):
    self.run_test_case(self.scenario.deploy_deployment('library/nginx'))

  def test_b1_deploy_service(self):
    self.run_test_case(self.scenario.deploy_service())

  def test_b2_check_manifest_endpoint(self):
    self.run_test_case(self.scenario.check_manifest_endpoint_exists('deployment'))

  def test_b2_check_applications_endpoint(self):
    self.run_test_case(self.scenario.check_applications_endpoint())

  def test_b2_check_clusters_endpoint(self):
    self.run_test_case(self.scenario.check_clusters_endpoint('deployment'))

  def test_b2_check_detailed_clusters_endpoint(self):
    self.run_test_case(self.scenario.check_detailed_clusters_endpoint('deployment'))

  def test_b2_check_server_groups_endpoint(self):
    self.run_test_case(self.scenario.check_server_groups_endpoint('deployment', 'library/nginx'))

  def test_b2_check_load_balancers_endpoint(self):
    self.run_test_case(self.scenario.check_load_balancers_endpoint('service'))

  def test_b3_delete_service(self):
    self.run_test_case(self.scenario.delete_kind('service'), max_retries=2)

  def test_b4_check_server_groups_endpoint_no_load_balancer(self):
    self.run_test_case(self.scenario.check_server_groups_endpoint('deployment', 'library/nginx', has_lb=False))

  def test_b9_delete_deployment(self):
    self.run_test_case(self.scenario.delete_kind('deployment'), max_retries=2)

  def test_z_delete_app(self):
    # Give a total of a minute because it might also need
    # an internal cache update
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)


def main():
  """Implements the main method running this cache test."""

  defaults = {
      'TEST_STACK': 'tst',
      'TEST_APP': 'kubv2cache' + KubeV2CacheTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[KubeV2CacheTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[KubeV2CacheTest])


if __name__ == '__main__':
  sys.exit(main())
