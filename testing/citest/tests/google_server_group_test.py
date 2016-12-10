# Standard python modules.
import time
import sys

# citest modules.
import citest.gcp_testing as gcp
import citest.json_predicate as jp
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate
import citest.base


class GoogleServerGroupTestScenario(sk.SpinnakerTestScenario):

  MINIMUM_PROJECT_QUOTA = {
      'INSTANCE_TEMPLATES': 1,
      'HEALTH_CHECKS': 1,
      'FORWARDING_RULES': 1,
      'IN_USE_ADDRESSES': 3,
      'TARGET_POOLS': 1,
  }

  MINIMUM_REGION_QUOTA = {
      'CPUS': 3,
      'IN_USE_ADDRESSES': 3,
      'INSTANCE_GROUP_MANAGERS': 2,
      'INSTANCES': 3,
  }

  @classmethod
  def new_agent(cls, bindings):
    '''Implements the base class interface to create a new agent.

    This method is called by the base classes during setup/initialization.

    Args:
      bindings: The bindings dictionary with configuration information
        that this factory can draw from to initialize. If the factory would
        like additional custom bindings it could add them to initArgumentParser.

    Returns:
      A citest.service_testing.BaseAgent that can interact with Gate.
      This is the agent that test operations will be posted to.
    '''
    return gate.new_agent(bindings)

  def __init__(self, bindings, agent=None):
    super(GoogleServerGroupTestScenario, self).__init__(bindings, agent)

    # Our application name and path to post events to.
    self.TEST_APP = bindings['TEST_APP']
    self.__path = 'applications/%s/tasks' % self.TEST_APP

    # The spinnaker stack decorator for our resources.
    self.TEST_STACK = bindings['TEST_STACK']

    self.TEST_REGION = bindings['TEST_GCE_REGION']
    self.TEST_ZONE = bindings['TEST_GCE_ZONE']

    # Resource names used among tests.
    self.__cluster_name = '%s-%s' % (self.TEST_APP, self.TEST_STACK)
    self.__server_group_name = '%s-v000' % self.__cluster_name
    self.__cloned_server_group_name = '%s-v001' % self.__cluster_name
    self.__lb_name = '%s-%s-fe' % (self.TEST_APP, self.TEST_STACK)

  def create_load_balancer(self):
    job = [{
      'cloudProvider': 'gce',
      'loadBalancerName': self.__lb_name,
      'ipProtocol': 'TCP',
      'portRange': '8080',
      'provider': 'gce',
      'stack': self.TEST_STACK,
      'detail': 'frontend',
      'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      'region': self.TEST_REGION,
      'listeners': [{
        'protocol': 'TCP',
        'portRange': '8080',
        'healthCheck': False
      }],
      'name': self.__lb_name,
      'type': 'upsertLoadBalancer',
      'availabilityZones': {self.TEST_REGION: []},
      'user': 'integration-tests'
    }]

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Load Balancer Created', retryable_for_secs=30)
     .list_resource('forwardingRules')
     .contains_path_value('name', self.__lb_name))

    payload = self.agent.make_json_payload_from_kwargs(
        job=job, description='Server Group Test - create load balancer',
        application=self.TEST_APP)

    return st.OperationContract(
      self.new_post_operation(
          title='create_load_balancer', data=payload, path=self.__path),
      contract=builder.build())

  def create_instances(self):
    job = [{
      'application': self.TEST_APP,
      'stack': self.TEST_STACK,
      'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      'zone': self.TEST_ZONE,
      'network': 'default',
      'targetSize': 1,
      'capacity': {
        'min': 1,
        'max': 1,
        'desired': 1
      },
      'availabilityZones': {
        self.TEST_REGION: [self.TEST_ZONE]
      },
      'loadBalancers': [self.__lb_name],
      'instanceMetadata': {
        'load-balancer-names': self.__lb_name
      },
      'cloudProvider': 'gce',
      'image': self.bindings['TEST_GCE_IMAGE_NAME'],
      'instanceType': 'f1-micro',
      'initialNumReplicas': 1,
      'type': 'createServerGroup',
      'account': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      'user': 'integration-tests'
    }]

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Instance Created', retryable_for_secs=150)
     .list_resource('instanceGroups')
     .contains_path_value('name', self.__server_group_name))

    payload = self.agent.make_json_payload_from_kwargs(
        job=job,
        description='Server Group Test - create initial server group',
        application=self.TEST_APP)

    return st.OperationContract(
      self.new_post_operation(
          title='create_instances', data=payload, path=self.__path),
      contract=builder.build())

  def resize_server_group(self):
    job = [{
      'targetSize': 2,
      'capacity': {
        'min': 2,
        'max': 2,
        'desired': 2
      },
      'replicaPoolName': self.__server_group_name,
      'numReplicas': 2,
      'region': self.TEST_REGION,
      'zone': self.TEST_ZONE,
      'asgName': self.__server_group_name,
      'serverGroupName': self.__server_group_name,
      'type': 'resizeServerGroup',
      'regions': [self.TEST_REGION],
      'zones': [self.TEST_ZONE],
      'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      'cloudProvider': 'gce',
      'user': 'integration-tests'
    }]

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Server Group Resized', retryable_for_secs=90)
     .inspect_resource('instanceGroups',
                       self.__server_group_name,
                       ['--zone', self.TEST_ZONE])
     .contains_path_eq('size', 2))

    payload = self.agent.make_json_payload_from_kwargs(
        job=job, description='Server Group Test - resize to 2 instances',
        application=self.TEST_APP)

    return st.OperationContract(
      self.new_post_operation(
          title='resize_instances', data=payload, path=self.__path),
      contract=builder.build())

  def clone_server_group(self):
    job = [{
      'application': self.TEST_APP,
      'stack': self.TEST_STACK,
      'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      'loadBalancers': [self.__lb_name],
      'targetSize': 1,
      'capacity': {
        'min': 1,
        'max': 1,
        'desired': 1
      },
      'zone': self.TEST_ZONE,
      'network': 'default',
      'instanceMetadata': {'load-balancer-names': self.__lb_name},
      'availabilityZones': {self.TEST_REGION: [self.TEST_ZONE]},
      'cloudProvider': 'gce',
      'source': {
        'account': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
        'region': self.TEST_REGION,
        'zone': self.TEST_ZONE,
        'serverGroupName': self.__server_group_name,
        'asgName': self.__server_group_name
      },
      'instanceType': 'f1-micro',
      'image': self.bindings['TEST_GCE_IMAGE_NAME'],
      'initialNumReplicas': 1,
      'loadBalancers': [self.__lb_name],
      'type': 'cloneServerGroup',
      'account': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      'user': 'integration-tests'
    }]

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Server Group Cloned', retryable_for_secs=90)
     .list_resource('instanceGroupManagers')
     .contains_path_value('baseInstanceName', self.__cloned_server_group_name))

    payload = self.agent.make_json_payload_from_kwargs(
        job=job, description='Server Group Test - clone server group',
        application=self.TEST_APP)

    return st.OperationContract(
      self.new_post_operation(
          title='clone_server_group', data=payload, path=self.__path),
      contract=builder.build())

  def disable_server_group(self):
    job = [{
      'cloudProvider': 'gce',
      'asgName': self.__server_group_name,
      'serverGroupName': self.__server_group_name,
      'region': self.TEST_REGION,
      'zone': self.TEST_ZONE,
      'type': 'disableServerGroup',
      'regions': [self.TEST_REGION],
      'zones': [self.TEST_ZONE],
      'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      'user': 'integration-tests'
    }]

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Server Group Disabled', retryable_for_secs=90)
     .list_resource('instanceGroupManagers')
     .contains_path_value('baseInstanceName', self.__server_group_name)
     .excludes_match({
          'baseInstanceName': jp.STR_SUBSTR(self.__server_group_name),
          'targetPools': jp.LIST_MATCHES([jp.STR_SUBSTR('https')])
          }))

    payload = self.agent.make_json_payload_from_kwargs(
        job=job, description='Server Group Test - disable server group',
        application=self.TEST_APP)

    return st.OperationContract(
      self.new_post_operation(
          title='disable_server_group', data=payload, path=self.__path),
      contract=builder.build())

  def enable_server_group(self):
    job = [{
      'cloudProvider': 'gce',
      'asgName': self.__server_group_name,
      'serverGroupName': self.__server_group_name,
      'region': self.TEST_REGION,
      'zone': self.TEST_ZONE,
      'type': 'enableServerGroup',
      'regions': [self.TEST_REGION],
      'zones': [self.TEST_ZONE],
      'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      'user': 'integration-tests'
    }]

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Server Group Enabled', retryable_for_secs=90)
     .list_resource('instanceGroupManagers')
     .contains_match({
          'baseInstanceName': jp.STR_SUBSTR(self.__server_group_name),
          'targetPools': jp.LIST_MATCHES([jp.STR_SUBSTR( 'https')])
          }))

    payload = self.agent.make_json_payload_from_kwargs(
        job=job, description='Server Group Test - enable server group',
        application=self.TEST_APP)

    return st.OperationContract(
      self.new_post_operation(
          title='enable_server_group', data=payload, path=self.__path),
      contract=builder.build())

  def destroy_server_group(self, version):
    serverGroupName = '%s-%s' % (self.__cluster_name, version)
    job = [{
      'cloudProvider': 'gce',
      'asgName': serverGroupName,
      'serverGroupName': serverGroupName,
      'region': self.TEST_REGION,
      'zone': self.TEST_ZONE,
      'type': 'destroyServerGroup',
      'regions': [self.TEST_REGION],
      'zones': [self.TEST_ZONE],
      'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      'user': 'integration-tests'
    }]

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Server Group Destroyed', retryable_for_secs=90)
     .list_resource('instanceGroupManagers')
     .excludes_path_value('baseInstanceName', serverGroupName))

    payload = self.agent.make_json_payload_from_kwargs(
        job=job, description='Server Group Test - destroy server group',
        application=self.TEST_APP)

    return st.OperationContract(
      self.new_post_operation(
          title='destroy_server_group', data=payload, path=self.__path),
      contract=builder.build())

  def delete_load_balancer(self):
    job = [{
      "loadBalancerName": self.__lb_name,
      "networkLoadBalancerName": self.__lb_name,
      "region": "us-central1",
      "type": "deleteLoadBalancer",
      "regions": ["us-central1"],
      "credentials": self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
      "cloudProvider": "gce",
      "user": "integration-tests"
    }]

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Load Balancer Created', retryable_for_secs=30)
     .list_resource('forwardingRules')
     .excludes_path_value('name', self.__lb_name))

    payload = self.agent.make_json_payload_from_kwargs(
        job=job, description='Server Group Test - delete load balancer',
        application=self.TEST_APP)

    return st.OperationContract(
      self.new_post_operation(
          title='delete_load_balancer', data=payload, path=self.__path),
      contract=builder.build())


class GoogleServerGroupTest(st.AgentTestCase):

  @staticmethod
  def setUpClass():
    runner = citest.base.TestRunner.global_runner()
    scenario = runner.get_shared_data(GoogleServerGroupTestScenario)
    managed_region = scenario.bindings['TEST_GCE_REGION']
    title = 'Check Quota for {0}'.format(scenario.__class__.__name__)

    verify_results = gcp.verify_quota(
        title,
        scenario.gcp_observer,
        project_quota=GoogleServerGroupTestScenario.MINIMUM_PROJECT_QUOTA,
        regions=[(managed_region,
                  GoogleServerGroupTestScenario.MINIMUM_REGION_QUOTA)])
    if not verify_results:
      raise RuntimeError('Insufficient Quota: {0}'.format(verify_results))

  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        GoogleServerGroupTestScenario)

  def test_a_create_load_balancer(self):
    self.run_test_case(self.scenario.create_load_balancer())

  def test_b_create_server_group(self):
    self.run_test_case(self.scenario.create_instances())

  def test_c_resize_server_group(self):
    self.run_test_case(self.scenario.resize_server_group())

  def test_d_clone_server_group(self):
    self.run_test_case(self.scenario.clone_server_group(),
                       # TODO(ewiseblatt): 20160314
                       # There is a lock contention race condition
                       # in clouddriver that causes intermittent failure.
                       max_retries=5)

  def test_e_disable_server_group(self):
    self.run_test_case(self.scenario.disable_server_group())

  def test_f_enable_server_group(self):
    self.run_test_case(self.scenario.enable_server_group())

  def test_g_destroy_server_group_v000(self):
    self.run_test_case(self.scenario.destroy_server_group('v000'))

  def test_h_destroy_server_group_v001(self):
    self.run_test_case(self.scenario.destroy_server_group('v001'))

  def test_z_delete_load_balancer(self):
    self.run_test_case(self.scenario.delete_load_balancer())


def main():

  defaults = {
    'TEST_STACK': GoogleServerGroupTestScenario.DEFAULT_TEST_ID,
    'TEST_APP': 'gcpsvrgrptst' + GoogleServerGroupTestScenario.DEFAULT_TEST_ID
  }

  return citest.base.TestRunner.main(
      parser_inits=[GoogleServerGroupTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[GoogleServerGroupTest])


if __name__ == '__main__':
  sys.exit(main())
