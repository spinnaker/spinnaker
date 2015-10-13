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
_IMAGE = 'ubuntu-1404-trusty-v20150909a'

class ServerGroupTestScenario(sk.SpinnakerTestScenario):
  app = 'servergrouptests%s' % _TEST_DECORATOR
  stack = 'sg'
  cluster_name = '%s-%s' % (app, stack)
  server_group_name = '%s-v000' % cluster_name
  cloned_server_group_name = '%s-v001' % cluster_name
  lb_name = '%s-%s-frontend' % (app, stack)

  path = 'applications/%s/tasks' % app

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
      'loadBalancerName': self.lb_name,
      'ipProtocol': 'TCP',
      'portRange': '8080',
      'provider': 'gce',
      'stack': self.stack,
      'detail': 'frontend',
      'credentials': self.bindings['GCE_CREDENTIALS'],
      'region': self.bindings['TEST_GCE_REGION'],
      'listeners': [{
        'protocol': 'TCP',
        'portRange': '8080',
        'healthCheck': False
      }],
      'name': self.lb_name,
      'providerType': 'gce',
      'type': 'upsertAmazonLoadBalancer',
      'availabilityZones': {self.bindings['TEST_GCE_REGION']: []},
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Load Balancer Created', retryable_for_secs=30)
     .list_resources('forwarding-rules')
     .contains('name', self.lb_name))

    payload = self.agent.make_payload(job, description='Server Group Tests - create load balancer', application=self.app)

    return st.OperationContract(
      self.new_post_operation(title='create_load_balancer', data=payload, path=self.path),
      contract=builder.build())

  def create_instances(self):
    job = [{
      'application': self.app,
      'stack': self.stack,
      'credentials': self.bindings['GCE_CREDENTIALS'],
      'zone': self.bindings['TEST_GCE_ZONE'],
      'network': 'default',
      'capacity': {
        'min': 1,
        'max': 1,
        'desired': 1
      },
      'availabilityZones': {
        self.bindings['TEST_GCE_REGION']: [self.bindings['TEST_GCE_ZONE']]
      },
      'loadBalancers': [self.lb_name],
      'instanceMetadata': {
        'load-balancer-names': self.lb_name
      },
      'cloudProvider': 'gce',
      'providerType': 'gce',
      'image': _IMAGE,
      'instanceType': 'f1-micro',
      'initialNumReplicas': 1,
      'type': 'linearDeploy',
      'account': self.bindings['GCE_CREDENTIALS'],
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Instance Created', retryable_for_secs=150)
     .list_resources('instance-groups')
     .contains('name', self.server_group_name))

    payload = self.agent.make_payload(job, description='Server Group Tests - create initial server group', application=self.app)

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
      'region': self.bindings['TEST_GCE_REGION'],
      'zone': self.bindings['TEST_GCE_ZONE'],
      'asgName': self.server_group_name,
      'type': 'resizeServerGroup',
      'regions': [self.bindings['TEST_GCE_REGION']],
      'zones': [self.bindings['TEST_GCE_ZONE']],
      'credentials': self.bindings['GCE_CREDENTIALS'],
      'cloudProvider': 'gce',
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Server Group Resized', retryable_for_secs=90)
     .inspect_resource('instance-groups', self.server_group_name, ['--zone', self.bindings['TEST_GCE_ZONE']])
     .contains_eq('size', 2))

    payload = self.agent.make_payload(job, description='Server Group Tests - resize to 2 instances', application=self.app)

    return st.OperationContract(
      self.new_post_operation(title='resize_instances', data=payload, path=self.path),
      contract=builder.build())

  def clone_server_group(self):
    job = [{
      'application': self.app,
      'stack': self.stack,
      'credentials': self.bindings['GCE_CREDENTIALS'],
      'loadBalancers': [self.lb_name],
      'capacity': {
        'min': 1,
        'max': 1,
        'desired': 1
      },
      'zone': self.bindings['TEST_GCE_ZONE'],
      'network': 'default',
      'instanceMetadata': {'load-balancer-names': self.lb_name},
      'availabilityZones': {self.bindings['TEST_GCE_REGION']: [self.bindings['TEST_GCE_ZONE']]},
      'cloudProvider': 'gce',
      'providerType': 'gce',
      'source': {
        'account': self.bindings['GCE_CREDENTIALS'],
        'region': self.bindings['TEST_GCE_REGION'],
        'zone': self.bindings['TEST_GCE_ZONE'],
        'serverGroupName': self.server_group_name,
        'asgName': self.server_group_name
      },
      'instanceType': 'f1-micro',
      'image': 'ubuntu-1404-trusty-v20150909a',
      'initialNumReplicas': 1,
      'loadBalancers': [self.lb_name],
      'type': 'copyLastAsg',
      'account': self.bindings['GCE_CREDENTIALS'],
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Server Group Cloned', retryable_for_secs=90)
     .list_resources('managed-instance-groups')
     .contains('baseInstanceName', self.cloned_server_group_name))

    payload = self.agent.make_payload(job, description='Server Group Tests - clone server group', application=self.app)

    return st.OperationContract(
      self.new_post_operation(title='clone_server_group', data=payload, path=self.path),
      contract=builder.build())

  def disable_server_group(self):
    job = [{
      'cloudProvider': 'gce',
      'asgName': self.server_group_name,
      'serverGroupName': self.server_group_name,
      'region': self.bindings['TEST_GCE_REGION'],
      'zone': self.bindings['TEST_GCE_ZONE'],
      'type': 'disableServerGroup',
      'regions': [self.bindings['TEST_GCE_REGION']],
      'zones': [self.bindings['TEST_GCE_ZONE']],
      'credentials': self.bindings['GCE_CREDENTIALS'],
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Server Group Disabled', retryable_for_secs=90)
     .list_resources('managed-instance-groups')
     .contains('baseInstanceName', self.server_group_name)
     .excludes_group([jc.PathContainsPredicate('baseInstanceName', self.server_group_name),
                      jc.PathContainsPredicate('targetPools', 'https')]))

    payload = self.agent.make_payload(job, description='Server Group Tests - disable server group', application=self.app)

    return st.OperationContract(
      self.new_post_operation(title='disable_server_group', data=payload, path=self.path),
      contract=builder.build())

  def enable_server_group(self):
    job = [{
      'cloudProvider': 'gce',
      'asgName': self.server_group_name,
      'serverGroupName': self.server_group_name,
      'region': self.bindings['TEST_GCE_REGION'],
      'zone': self.bindings['TEST_GCE_ZONE'],
      'type': 'enableServerGroup',
      'regions': [self.bindings['TEST_GCE_REGION']],
      'zones': [self.bindings['TEST_GCE_ZONE']],
      'credentials': self.bindings['GCE_CREDENTIALS'],
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Server Group Enabled', retryable_for_secs=90)
     .list_resources('managed-instance-groups')
     .contains_group([jc.PathContainsPredicate('baseInstanceName', self.server_group_name),
                      jc.PathContainsPredicate('targetPools', 'https')]))

    payload = self.agent.make_payload(job, description='Server Group Tests - enable server group', application=self.app)

    return st.OperationContract(
      self.new_post_operation(title='enable_server_group', data=payload, path=self.path),
      contract=builder.build())

  def destroy_server_group(self, version):
    serverGroupName = '%s-%s' % (self.cluster_name, version)
    job = [{
      'cloudProvider': 'gce',
      'asgName': serverGroupName,
      'serverGroupName': serverGroupName,
      'region': self.bindings['TEST_GCE_REGION'],
      'zone': self.bindings['TEST_GCE_ZONE'],
      'type': 'destroyServerGroup',
      'regions': [self.bindings['TEST_GCE_REGION']],
      'zones': [self.bindings['TEST_GCE_ZONE']],
      'credentials': self.bindings['GCE_CREDENTIALS'],
      'user': 'integration-tests'
    }]

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Server Group Destroyed', retryable_for_secs=90)
     .list_resources('managed-instance-groups')
     .excludes('baseInstanceName', serverGroupName))

    payload = self.agent.make_payload(job, description='Server Group Tests - destroy server group', application=self.app)

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

    payload = self.agent.make_payload(job, description='Server Group Tests - delete load balancer', application=self.app)

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

  def test_z_delete_load_balancer(self):
    self.run_test_case(self.scenario.delete_load_balancer())


def main():
  ServerGroupIntegrationTest.main(ServerGroupTestScenario)


if __name__ == '__main__':
  main()
  sys.exit(0)
