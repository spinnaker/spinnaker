# Standard python modules.
import time
import sys

# citest modules.
import citest.gcp_testing as gcp
import citest.json_contract as jc
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate

_TEST_DECORATOR = time.strftime('%Y%m%d%H%M%S')


class ServerGroupTestScenario(sk.SpinnakerTestScenario):
  app = 'intg'
  stack = 'servergroup%s' % _TEST_DECORATOR
  cluster_name = 'intg-%s' % stack
  server_group_name = '%s-v000' % cluster_name
  cloned_server_group_name = '%s-v001' % cluster_name
  lb_name = 'intg-%s-frontend' % stack

  path = 'applications/intg/tasks'

  @classmethod
  def new_agent(cls, bindings):
    '''Implements the base class interface to create a new agent.

    This method is called by the base classes during setup/initialization.

    Args:
      bindings: The bindings dictionary with configuration information
        that this factory can draw from to initialize. If the factory would
        like additional custom bindings it could add them to initArgumentParser.

    Returns:
      A citest.service_testing.TestableAgent that can interact with Gate.
      This is the agent that test operations will be posted to.
    '''
    return gate.new_agent(bindings)

  def create_load_balancer(self):
    job = [{
      'cloudProvider': 'gce',
      'networkLoadBalancerName': self.lb_name,
      'ipProtocol': 'TCP',
      'portRange': '8080',
      'provider': 'gce',
      'stack': self.stack,
      'detail': 'frontend',
      'credentials': 'my-account-name',
      'region': 'us-central1',
      'listeners': [{
        'protocol': 'TCP',
        'portRange': '8080',
        'healthCheck': False
      }],
      'name': self.lb_name,
      'providerType': 'gce',
      'type': 'upsertAmazonLoadBalancer',
      'availabilityZones': {'us-central1': []},
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Load Balancer Created', retryable_for_secs=30)
     .list_resources('forwarding-rules')
     .contains('name', self.lb_name))

    payload = self.agent.make_payload(job, 'Server Group Tests - create load balancer', 'gate')

    return st.OperationContract(
      self.new_post_operation(title='create_load_balancer', data=payload, path=self.path),
      contract=builder.build())

  def create_instances(self):
    job = [{
      'application': self.app,
      'stack': self.stack,
      'credentials': 'my-account-name',
      'zone': 'us-central1-f',
      'network': 'default',
      'capacity': {
        'min': 1,
        'max': 1,
        'desired': 1
      },
      'availabilityZones': {
        'us-central1': ['us-central1-f']
      },
      'loadBalancers': [self.lb_name],
      'networkLoadBalancers': [self.lb_name],
      'instanceMetadata': {
        'load-balancer-names': self.lb_name
      },
      'cloudProvider': 'gce',
      'providerType': 'gce',
      'image': 'ubuntu-1404-trusty-v20150909a',
      'instanceType': 'f1-micro',
      'initialNumReplicas': 1,
      'type': 'linearDeploy',
      'account': 'my-account-name',
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Instance Created', retryable_for_secs=150)
     .list_resources('instance-groups')
     .contains('name', self.server_group_name))

    payload = self.agent.make_payload(job, 'Server Group Tests - create initial server group', 'gate')

    return st.OperationContract(
      self.new_post_operation(title='create_instances', data=payload, path=self.path),
      contract=builder.build())

  def resize_server_group(self):
    job = [{
      'capacity': {
        'min': 2,
        'max': 2,
        'desired': 2
      },
      'replicaPoolName': self.server_group_name,
      'numReplicas': 2,
      'region': 'us-central1',
      'zone': 'us-central1-f',
      'asgName': self.server_group_name,
      'type': 'resizeServerGroup',
      'regions': ['us-central1'],
      'zones': ['us-central1-f'],
      'credentials': 'my-account-name',
      'cloudProvider': 'gce',
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Server Group Resized', retryable_for_secs=90)
     .inspect_resource('instance-groups', self.server_group_name, ['--zone', 'us-central1-f'])
     .contains_eq('size', 2))

    payload = self.agent.make_payload(job, 'Server Group Tests - resize to 2 instances', 'gate')

    return st.OperationContract(
      self.new_post_operation(title='resize_instances', data=payload, path=self.path),
      contract=builder.build())

  def clone_server_group(self):
    job = [{
      'application': self.app,
      'stack': self.stack,
      'credentials': 'my-account-name',
      'loadBalancers': [self.lb_name],
      'capacity': {
        'min': 1,
        'max': 1,
        'desired': 1
      },
      'zone': 'us-central1-f',
      'network': 'default',
      'instanceMetadata': {'load-balancer-names': self.lb_name},
      'availabilityZones': {'us-central1': ['us-central1-f']},
      'cloudProvider': 'gce',
      'providerType': 'gce',
      'source': {
        'account': 'my-account-name',
        'region': 'us-central1',
        'zone': 'us-central1-f',
        'serverGroupName': self.server_group_name,
        'asgName': self.server_group_name
      },
      'instanceType': 'f1-micro',
      'image': 'ubuntu-1404-trusty-v20150909a',
      'initialNumReplicas': 1,
      'networkLoadBalancers': [self.lb_name],
      'type': 'copyLastAsg',
      'account': 'my-account-name',
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Server Group Cloned', retryable_for_secs=90)
     .list_resources('instance-groups')
     .contains('name', self.cloned_server_group_name))

    payload = self.agent.make_payload(job, 'Server Group Tests - clone server group', 'gate')

    return st.OperationContract(
      self.new_post_operation(title='clone_server_group', data=payload, path=self.path),
      contract=builder.build())

  def disable_server_group(self):
    job = [{
      'cloudProvider': 'gce',
      'replicaPoolName': self.server_group_name,
      'asgName': self.server_group_name,
      'region': 'us-central1',
      'zone': 'us-central1-f',
      'type': 'disableAsg',
      'regions': ['us-central1'],
      'zones': ['us-central1-f'],
      'credentials': 'my-account-name',
      'providerType': 'gce',
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Server Group Disabled', retryable_for_secs=90)
     .list_resources('managed-instance-groups')
     .contains('baseInstanceName', self.server_group_name)
     .excludes_group([jc.PathContainsPredicate('baseInstanceName', self.server_group_name),
                      jc.PathContainsPredicate('targetPools', 'https')]))

    payload = self.agent.make_payload(job, 'Server Group Tests - disable server group', 'gate')

    return st.OperationContract(
      self.new_post_operation(title='disable_server_group', data=payload, path=self.path),
      contract=builder.build())

  def enable_server_group(self):
    job = [{
      'cloudProvider': 'gce',
      'replicaPoolName': self.server_group_name,
      'asgName': self.server_group_name,
      'region': 'us-central1',
      'zone': 'us-central1-f',
      'type': 'enableAsg',
      'regions': ['us-central1'],
      'zones': ['us-central1-f'],
      'credentials': 'my-account-name',
      'providerType': 'gce',
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Server Group Enabled', retryable_for_secs=90)
     .list_resources('managed-instance-groups')
     .contains_group([jc.PathContainsPredicate('baseInstanceName', self.server_group_name),
                      jc.PathContainsPredicate('targetPools', 'https')]))

    payload = self.agent.make_payload(job, 'Server Group Tests - enable server group', 'gate')

    return st.OperationContract(
      self.new_post_operation(title='enable_server_group', data=payload, path=self.path),
      contract=builder.build())

  # destroyServerGroup

  def destroy_server_group(self, version):
    serverGroupName = '%s-%s' % (self.cluster_name, version)
    job = [{
      'cloudProvider': 'gce',
      'asgName': serverGroupName,
      'serverGroupName': serverGroupName,
      'region': 'us-central1',
      'zone': 'us-central1-f',
      'type': 'destroyServerGroup',
      'regions': ['us-central1'],
      'zones': ['us-central1-f'],
      'credentials': 'my-account-name',
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Server Group Destroyed', retryable_for_secs=90)
     .list_resources('managed-instance-groups')
     .excludes('baseInstanceName', serverGroupName))

    payload = self.agent.make_payload(job, 'Server Group Tests - destroy server group', 'gate')

    return st.OperationContract(
      self.new_post_operation(title='destroy_server_group', data=payload, path=self.path),
      contract=builder.build())

  def delete_load_balancer(self):
    job = [{
      "loadBalancerName": self.lb_name,
      "networkLoadBalancerName": self.lb_name,
      "region": "us-central1",
      "type": "deleteLoadBalancer",
      "regions": ["us-central1"],
      "credentials": "my-account-name",
      "cloudProvider": "gce",
      "user": "integration-tests"
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Load Balancer Created', retryable_for_secs=30)
     .list_resources('forwarding-rules')
     .excludes('name', self.lb_name))

    payload = self.agent.make_payload(job, 'Server Group Tests - delete load balancer', 'gate')

    return st.OperationContract(
      self.new_post_operation(title='delete_load_balancer', data=payload, path=self.path),
      contract=builder.build())


class ServerGroupIntegrationTest(st.AgentTestCase):
  def test_a_create_load_balancer(self):
    self.run_test_case(self.scenario.create_load_balancer())

  def test_b_create_server_group(self):
    self.run_test_case(self.scenario.create_instances())

  def test_c_resize_server_group(self):
    self.run_test_case(self.scenario.resize_server_group())

  def test_d_clone_server_group(self):
    self.run_test_case(self.scenario.clone_server_group())

  def test_e_disable_server_group(self):
    self.run_test_case(self.scenario.disable_server_group())

  def test_f_enable_server_group(self):
    self.run_test_case(self.scenario.enable_server_group())

  def test_g_destroy_server_group_v000(self):
    self.run_test_case(self.scenario.destroy_server_group('v000'))

  def test_h_destroy_server_group_v001(self):
    self.run_test_case(self.scenario.destroy_server_group('v001'))

  def test_i_delete_load_balancer(self):
    self.run_test_case(self.scenario.delete_load_balancer())


def main():
  ServerGroupIntegrationTest.main(ServerGroupTestScenario)


if __name__ == '__main__':
  main()
  sys.exit(0)
