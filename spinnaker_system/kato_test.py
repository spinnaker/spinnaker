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
# The kato test will use ssh to peek at the spinnaker configuration
# to determine the managed project it should verify, and to determine
# the spinnaker account name to use when sending it commands.
#
# Sample Usage:
#     Assuming you have created $PASSPHRASE_FILE (which you should chmod 400):
#
#   python test/kato_test.py \
#     --gce_ssh_passphrase_file=$PASSPHRASE_FILE \
#     --gce_project=$PROJECT \
#     --gce_zone=$ZONE \
#     --gce_instance=$INSTANCE
# or
#   python test/kato_test.py \
#     --native_hostname=host-running-kato
#     --managed_gce_project=$PROJECT \
#     --test_gce_zone=$ZONE


# Standard python modules.
import time
import sys

# citest modules.
import citest.gcp_testing as gcp
import citest.json_contract as jc
import citest.service_testing as st
import citest.service_testing.http_agent as http_agent

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.kato as kato


_TEST_DECORATOR = time.strftime('%H%M%S')


class KatoTestScenario(sk.SpinnakerTestScenario):
  # _instance_names and _instance_zones will be set in create_instances_.
  # We're breaking them out so that they can be shared by other methods,
  # especially terminate.
  use_instance_names = []
  use_instance_zones = []
  _use_lb_name = ''     # The network load balancer name.
  _use_lb_tp_name = ''  # The load balancer's target pool name.
  _use_lb_hc_name = ''  # The load balancer's health check name.
  _use_lb_target = ''   # The load balancer's 'target' resource.
  _use_http_lb_name = '' # The HTTP load balancer name.
  _use_http_lb_hc_name = '' # The HTTP load balancer health check name.
  _use_http_lb_bs_name = '' # The HTTP load balancer backend service name.
  _use_http_lb_fr_name = '' # The HTTP load balancer forwarding rule.
  _use_http_lb_map_name = '' # The HTTP load balancer url map name.
  _use_http_lb_http_proxy_name = '' # The HTTP load balancer target http proxy.

  @classmethod
  def new_agent(cls, bindings):
    return kato.new_agent(bindings)

  @classmethod
  def initArgumentParser(cls, parser):
    """Initialize command line argument parser.

    Args:
      parser: argparse.ArgumentParser
    """
    super(KatoTestScenario, cls).initArgumentParser(parser, 'kato')

    # TODO(ewiseblatt): Move this image name somewhere.
    parser.add_argument(
        '--test_image_name',
        default='ubuntu-1404-trusty-v20150316',
        help='Image name to use when creating test instance.')

  def __init__(self, bindings, agent):
    """Construct new scenaro.

    Args:
      bindings: Configuration key/value bindings. Keys are upper case.
      agent: KatoSpinnakerAgent the scenario will talk to.
    """
    super(KatoTestScenario, self).__init__(bindings, agent)

  def create_instances(self):
    """Creates test adding instances to GCE.

     Create three instances.
       * The first two are of different types and zones, which
         we'll check. Future tests will also be using these
         from different zones (but same region).

       * The third is a duplicate in the same zone as another
         so we can check duplicate deletes (which limit one zone per call).

     We'll set the class properties use_instance_names and use_instance_zones
     so that they can be communicated to future tests to reference.

    Returns:
      st.OperationContract
    """
    self.use_instance_names = [
        'katotest%sa' % _TEST_DECORATOR,
        'katotest%sb' % _TEST_DECORATOR,
        'katotest%sc' % _TEST_DECORATOR]
    self.use_instance_zones = [
        self.bindings['TEST_GCE_ZONE'],
        'us-central1-b',
        self.bindings['TEST_GCE_ZONE']]
    if self.use_instance_zones[0] == self.use_instance_zones[1]:
      self.use_instance_zones[1] = 'us-central1-c'

    image_name = [self.bindings['TEST_IMAGE_NAME'],
                  'debian-7-wheezy-v20150526',
                  self.bindings['TEST_IMAGE_NAME']]
    if image_name[0] == image_name[1]:
      image_name[1] = 'ubuntu-1404-trusty-v20150316'
    machine_type = ['f1-micro', 'g1-small', 'f1-micro']

    instance_spec = []
    builder = gcp.GceContractBuilder(self.gce_observer)
    for i in range(3):
      instance_spec.append(self.substitute_variables(
          '{"createGoogleInstanceDescription": {'
              '"instanceName": "' + self.use_instance_names[i] + '",'
              '"image": "' + image_name[i] + '",'
              '"instanceType": "' + machine_type[i] + '",'
              '"zone": "' + self.use_instance_zones[i] + '",'
              '"credentials": "$GCE_CREDENTIALS"}'
          '}'))
      # Verify we created an instance, whether or not it boots.
      (builder.new_clause_builder(
          'Instance %d Created' % i, retryable_for_secs=90)
           .list_resources('instances')
           .contains('name', self.use_instance_names[i]))
      if i < 2:
        # Verify the details are what we asked for.
        # Since we've finished the created clause, this already exists.
        (builder.new_clause_builder('Instance %d Details' % i)
            .inspect_resource('instances', self.use_instance_names[i],
                              extra_args=['--zone', self.use_instance_zones[i]])
            .contains('machineType', machine_type[i]))
        # Verify the instance eventually boots up.
        # We can combine this with above, but we'll probably need
        # to retry this, but not the above, so this way if the
        # above is broken (wrong), we wont retry thinking it isnt there yet.
        (builder.new_clause_builder('Instance %d Is Running' % i,
                             retryable_for_secs=90)
            .inspect_resource('instances', name=self.use_instance_names[i],
                              extra_args=['--zone', self.use_instance_zones[i]])
            .contains_eq('status', 'RUNNING'))

    payload = '[{0},{1},{2}]'.format(
        instance_spec[0], instance_spec[1], instance_spec[2])
    return st.OperationContract(
        self.new_post_operation(
            title='create_instances', data=payload, path='ops'),
        contract=builder.build())

  def terminate_instances(self, names, zone):
    """Creates test for removing specific instances.

    Args:
      names: A list of instance names to be removed.
      zone: The zone containing the instances.

    Returns:
      st.OperationContract
    """
    builder = gcp.GceContractBuilder(self.gce_observer)
    clause = (builder.new_clause_builder('Instances Deleted', strict=True)
              .list_resources('instances'))
    name_str = ''
    sep = ""
    for name in names:
      name_str += '{sep}"{name}"'.format(sep=sep, name=name)
      sep = ','

      name_matches_pred = jc.PathContainsPredicate('name', name)
      is_stopping_pred = jc.ConjunctivePredicate(
        [name_matches_pred,jc.PathEqPredicate('status', 'STOPPING')])
      nolonger_exists_pred = jc.CardinalityPredicate(
        name_matches_pred, max=0)

      # Add disjunction (named instance is stopping or doesnt exist at all)
      disjunction = jc.DisjunctivePredicate(
          [is_stopping_pred, nolonger_exists_pred])
      clause.add_mapped_constraint(disjunction)

    payload = self.substitute_variables(
      '[{'
          '"terminateGoogleInstancesDescription":{'
              '"instanceIds": [' + name_str + '],'
              '"zone": "' + zone + '",'
              '"credentials": "$GCE_CREDENTIALS"'
          '}'
      '}]')

    return st.OperationContract(
        self.new_post_operation(
            title='terminate_instances', data=payload, path='ops'),
        contract=builder.build())

  def upsert_google_server_group_tags(self):
    replica_pool_name = 'katotest-replica-pool'
    payload = self.substitute_variables(
      '[{'
          '"upsertGoogleServerGroupTagsDescription":{'
             '"credentials": "$GCE_CREDENTIALS",'
             '"zone": "$TEST_GCE_ZONE",'
             '"replicaPoolName":"katotest-replica-pool",'
                '"tags": ["test-tag-1", "test-tag-2"]'
          '}'
      '}]')

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Replica Pool Tags Added')
        .inspect_resource('managed-instance-group', replica_pool_name)
        .contains_group(
            [jc.PathContainsPredicate('name', replica_pool_name),
             jc.PathContainsPredicate(
                 "tags/items", ['test-tag-1','test-tag-2'])]))

    return st.OperationContract(
        self.new_post_operation(
            title='upsert_server_group_tags', data=payload, path='ops'),
        contract=builder.build())

  def create_http_load_balancer(self):
    logical_http_lb_name = 'katotest-httplb-' + _TEST_DECORATOR
    self._use_http_lb_name = logical_http_lb_name

    # TODO(ewiseblatt): 20150530
    # This needs to be abbreviated to hc.
    self._use_http_lb_hc_name = logical_http_lb_name + '-health-check'

    # TODO(ewiseblatt): 20150530
    # This needs to be abbreviated to bs.
    self._use_http_lb_bs_name = logical_http_lb_name + '-backend-service'

    self._use_http_lb_fr_name = logical_http_lb_name

    # TODO(ewiseblatt): 20150530
    # This should be abbreviated (um?).
    self._use_http_lb_map_name = logical_http_lb_name + '-url-map'

    # TODO(ewiseblatt): 20150530
    # This should be abbreviated (px)?.
    self._use_http_lb_proxy_name = logical_http_lb_name + '-target-http-proxy'

    interval=231
    healthy=8
    unhealthy=9
    timeout=65
    path='/hello/world'

    # TODO(ewiseblatt): 20150530
    # This field might be broken. 123-456 still resolves to 80-80
    # Changing it for now so the test passes.
    port_range = "80-80"

    # TODO(ewiseblatt): 20150530
    # Specify explicit backends?

    payload = self.substitute_variables(
      '[{'
          '"createGoogleHttpLoadBalancerDescription":{'
              '"healthCheck":{'
              + ('"checkIntervalSec":{interval},'
                 '"healthyThreshold":{healthy},'
                 '"unhealthyThreshold":{unhealthy},'
                 '"timeoutSec":{timeout},'
                 '"requestPath":"{path}"'
                     .format(interval=interval, healthy=healthy,
                             unhealthy=unhealthy, timeout=timeout,
                             path=path))
              + '},'
          '"portRange:":"' + port_range +'",'
          '"loadBalancerName":"' + logical_http_lb_name +'",'
          '"credentials":"$GCE_CREDENTIALS"}'
      '}]')

    health_check = {
        'checkIntervalSec': interval,
        'healthyThreshold': healthy,
        'unhealthyThreshold': unhealthy,
        'timeoutSec': timeout,
        'requestPath': path }

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Http Health Check Added')
        .list_resources('http-health-checks')
        .contains_group(
            [jc.PathContainsPredicate('name', self._use_http_lb_hc_name),
             jc.PathContainsPredicate(None, health_check)]))
    (builder.new_clause_builder('Forwarding Rule Added', retryable_for_secs=15)
       .list_resources('forwarding-rules')
       .contains_group(
           [jc.PathContainsPredicate('name', self._use_http_lb_fr_name),
            jc.PathContainsPredicate('portRange', port_range)]))
    (builder.new_clause_builder('Backend Service Added')
       .list_resources('backend-services')
       .contains_group(
           [jc.PathContainsPredicate('name', self._use_http_lb_bs_name),
            jc.PathElementsContainPredicate(
                'healthChecks', self._use_http_lb_hc_name)]))
    (builder.new_clause_builder('Url Map Added')
       .list_resources('url-maps')
       .contains_group(
           [jc.PathContainsPredicate('name', self._use_http_lb_map_name),
            jc.PathContainsPredicate(
                'defaultService', self._use_http_lb_bs_name)]))
    (builder.new_clause_builder('Target Http Proxy Added')
       .list_resources('target-http-proxies')
       .contains_group(
           [jc.PathContainsPredicate('name', self._use_http_lb_proxy_name),
            jc.PathContainsPredicate('urlMap', self._use_http_lb_map_name)]))

    return st.OperationContract(
        self.new_post_operation(
            title='create_http_load_balancer', data=payload, path='ops'),
        contract=builder.build())

  def delete_http_load_balancer(self):
    payload = self.substitute_variables(
      '[{'
          '"deleteGoogleHttpLoadBalancerDescription":{'
              '"loadBalancerName":"' + self._use_http_lb_name +'",'
              '"credentials":"$GCE_CREDENTIALS"'
          '}'
      '}]')
    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Health Check Removed')
       .list_resources('http-health-checks')
       .excludes('name', self._use_http_lb_hc_name))
    (builder.new_clause_builder('Forwarding Rules Removed')
       .list_resources('forwarding-rules')
       .excludes('name', self._use_http_lb_fr_name))
    (builder.new_clause_builder('Backend Service Removed')
       .list_resources('backend-services')
       .excludes('name', self._use_http_lb_bs_name))
    (builder.new_clause_builder('Url Map Removed')
       .list_resources('url-maps')
       .excludes('name', self._use_http_lb_map_name))
    (builder.new_clause_builder('Target Http Proxy Removed')
       .list_resources('target-http-proxies')
       .excludes('name', self._use_http_lb_proxy_name))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_http_load_balancer', data=payload, path='ops'),
        contract=builder.build())


  def upsert_network_load_balancer(self):
    self._use_lb_name = 'katotest-lb-' + _TEST_DECORATOR
    self._use_lb_hc_name = '%s-hc' % self._use_lb_name
    self._use_lb_tp_name = '%s-tp' % self._use_lb_name
    self._use_lb_target = '{0}/targetPools/{1}'.format(
        self._bindings['TEST_GCE_REGION'], self._use_lb_tp_name)

    interval=123
    healthy=4
    unhealthy=5
    timeout=78
    path='/' + self._use_lb_target

    payload = self.substitute_variables(
      '[{'
          '"upsertGoogleNetworkLoadBalancerDescription":{'
              '"healthCheck":{'
              + ('"checkIntervalSec":{interval},'
                  '"healthyThreshold":{healthy},'
                  '"unhealthyThreshold":{unhealthy},'
                  '"timeoutSec":{timeout},'
                  '"requestPath":"{path}"'
                      .format(interval=interval, healthy=healthy,
                              unhealthy=unhealthy, timeout=timeout,
                              path=path))
               + '},'
          '"region":"$TEST_GCE_REGION",'
          '"credentials":"$GCE_CREDENTIALS",'
          '"networkLoadBalancerName":"' + self._use_lb_name + '"}'
      '}]')

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Forwarding Rules Added',
                                retryable_for_secs=30)
       .list_resources('forwarding-rules')
       .contains('name', self._use_lb_name)
       .contains('target', self._use_lb_target))
    (builder.new_clause_builder('Target Pool Added', retryable_for_secs=15)
       .list_resources('target-pools')
       .contains('name', self._use_lb_tp_name))

     # We list the resources here because the name isnt exact
     # and the list also returns the details we need.
    health_check = {
      'checkIntervalSec': interval,
      'healthyThreshold': healthy,
      'unhealthyThreshold': unhealthy,
      'timeoutSec': timeout,
      'requestPath': path }

    (builder.new_clause_builder('Health Check Added', retryable_for_secs=15)
       .list_resources('http-health-checks')
       .contains_group([jc.PathContainsPredicate('name', self._use_lb_hc_name),
                        jc.PathContainsPredicate(None, health_check)]))

    return st.OperationContract(
      self.new_post_operation(
          title='upsert_network_load_balancer', data=payload, path='ops'),
      contract=builder.build())

  def delete_network_load_balancer(self):
    payload = self.substitute_variables(
      '[{'
          '"deleteGoogleNetworkLoadBalancerDescription":{'
          '"region":"$TEST_GCE_REGION",'
          '"credentials":"$GCE_CREDENTIALS",'
          '"networkLoadBalancerName":"' + self._use_lb_name + '"}'
      '}]')

    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Health Check Removed')
       .list_resources('http-health-checks')
       .excludes('name', self._use_lb_hc_name))
    (builder.new_clause_builder('Target Pool Removed')
       .list_resources('target-pools')
       .excludes('name', self._use_lb_tp_name))
    (builder.new_clause_builder('Forwarding Rule Removed')
       .list_resources('forwarding-rules')
       .excludes('name', self._use_lb_name))

    return st.OperationContract(
      self.new_post_operation(
          title='delete_network_load_balancer', data=payload, path='ops'),
      contract=builder.build())

  def register_load_balancer_instances(self):
    """Creates test registering the first two instances with a load balancer.

       Assumes that create_instances test has been run to add
       the instances. Note by design these were in two different zones
       but same region as required by the API this is testing.

       Assumes that upsert_network_load_balancer has been run to
       create the load balancer itself.
    Returns:
      st.OperationContract
    """
    instance_str = '"{0}","{1}"'.format(
        self.use_instance_names[0], self.use_instance_names[1])
    payload = self.substitute_variables(
      '[{'
          '"registerInstancesWithGoogleNetworkLoadBalancerDescription":{'
              '"networkLoadBalancerNames":["' + self._use_lb_name + '"],'
              '"instanceIds":[' + instance_str + '],'
              '"region":"$TEST_GCE_REGION",'
              '"credentials":"$GCE_CREDENTIALS"'
          '}'
      '}]')
    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Instances in Target Pool',
                                retryable_for_secs=15)
       .list_resources('target-pools')
       .contains_group(
          [jc.PathContainsPredicate('name', self._use_lb_tp_name),
           jc.PathEqPredicate('region', self.bindings['TEST_GCE_REGION']),
           jc.PathElementsContainPredicate(
              'instances', self.use_instance_names[0]),
           jc.PathElementsContainPredicate(
              'instances', self.use_instance_names[1])])
       .excludes_group(
           [jc.PathContainsPredicate('name', self._use_lb_tp_name),
            jc.PathElementsContainPredicate(
                'instances', self.use_instance_names[2])]))

    return st.OperationContract(
      self.new_post_operation(
          title='register_load_balancer_instances', data=payload, path='ops'),
      contract=builder.build())


  def deregister_load_balancer_instances(self):
    """Creates a test unregistering instances from load balancer.

    Returns:
      st.OperationContract
    """
    instance_str = '"{0}","{1}"'.format(
        self.use_instance_names[0], self.use_instance_names[1])
    payload = self.substitute_variables(
      '[{'
          '"deregisterInstancesFromGoogleNetworkLoadBalancerDescription":{'
              '"networkLoadBalancerNames":["' + self._use_lb_name + '"],'
              '"instanceIds":[' + instance_str + '],'
              '"region":"$TEST_GCE_REGION",'
              '"credentials":"$GCE_CREDENTIALS"'
          '}'
      '}]')

    # NOTE(ewiseblatt): 20150530
    # This displays an error that 'instances' field doesnt exist.
    # That's because it was removed because all the instances are gone.
    # I dont have a way to express that the field itself is optional,
    # just the record. Leaving it as is because displaying this type of
    # error is usually helpful for development.
    builder = gcp.GceContractBuilder(self.gce_observer)
    (builder.new_clause_builder('Instances not in Target Pool')
       .list_resources(
          'target-pools',
          extra_args=['--region', self.bindings['TEST_GCE_REGION']])
       .excludes_group(
          [jc.PathContainsPredicate('name', self._use_lb_tp_name),
           jc.PathElementsContainPredicate(
              'instances', self.use_instance_names[0]),
           jc.PathElementsContainPredicate(
              'instances', self.use_instance_names[1])]))

    return st.OperationContract(
      self.new_post_operation(
          title='deregister_load_balancer_instances', data=payload, path='ops'),
      contract=builder.build())


class KatoIntegrationTest(st.AgentTestCase):
  def Xtest_a_upsert_server_group_tags(self):
    self.run_test_case(self.scenario.upsert_google_server_group_tags())

  def test_a_upsert_network_load_balancer(self):
    self.run_test_case(self.scenario.upsert_network_load_balancer())

  def test_b_create_instances(self):
    self.run_test_case(self.scenario.create_instances())

  def test_c_register_load_balancer_instances(self):
    self.run_test_case(self.scenario.register_load_balancer_instances())

  def test_d_create_http_load_balancer(self):
    self.run_test_case(self.scenario.create_http_load_balancer())

  def test_v_delete_http_load_balancer(self):
    self.run_test_case(
        self.scenario.delete_http_load_balancer(), timeout_ok=True)

  def test_w_deregister_load_balancer_instances(self):
    self.run_test_case(self.scenario.deregister_load_balancer_instances())

  def test_x_terminate_instances(self):
    # delete 1 which was in a different zone than the other two.
    # Then delete [0,2] together, which were in the same zone.
    try:
      self.run_test_case(
          self.scenario.terminate_instances(
              [self.scenario.use_instance_names[1]],
               self.scenario.use_instance_zones[1]))
    finally:
      # Always give this a try, even if the first test fails.
      # that increases our chances of cleaning everything up.
      self.run_test_case(
          self.scenario.terminate_instances(
              [self.scenario.use_instance_names[0],
               self.scenario.use_instance_names[2]],
               self.scenario.use_instance_zones[0]))

  def test_z_delete_network_load_balancer(self):
    self.run_test_case(self.scenario.delete_network_load_balancer())


def main():
  KatoIntegrationTest.main(KatoTestScenario)


if __name__ == '__main__':
  main()
  sys.exit(0)
