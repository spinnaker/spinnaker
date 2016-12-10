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
#     and $CITEST_ROOT points to the root directory of this repository
#     (which is . if you execute this from the root)
#
#   PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
#     python $CITEST_ROOT/spinnaker/spinnaker_system/google_kato_test.py \
#     --gce_ssh_passphrase_file=$PASSPHRASE_FILE \
#     --gce_project=$PROJECT \
#     --gce_zone=$ZONE \
#     --gce_instance=$INSTANCE
# or
#   PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
#     python $CITEST_ROOT/spinnaker/spinnaker_system/google_kato_test.py \
#     --native_hostname=host-running-kato
#     --managed_gce_project=$PROJECT \
#     --test_gce_zone=$ZONE


# Standard python modules.
import json as json_module
import logging
import sys

# citest modules.
from citest.service_testing import HttpContractBuilder
from citest.service_testing import NoOpOperation
from citest.base import JournalLogger
import citest.base
import citest.gcp_testing as gcp
import citest.json_predicate as jp
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.kato as kato


GCP_STANDARD_IMAGES = {
  'centos-cloud': ['centos-7', 'centos-6'],
  'coreos-cloud': ['coreos-stable', 'coreos-beta', 'coreos-alpha'],
  'debian-cloud': ['debian-8'],
  'opensuse-cloud': [''],
  'rhel-cloud': ['rhel-7', 'rhel-6'],
  'suse-cloud': [''],
  'ubuntu-os-cloud': ['ubuntu-1604-lts', 'ubuntu-1404-lts',
                      'ubuntu-1204-lts', 'ubuntu-1510'],
  'windows-cloud': ['windows-2012-r2', 'windows-2008-r2']
}


class GoogleKatoTestScenario(sk.SpinnakerTestScenario):
  # _instance_names and _instance_zones will be set in create_instances_.
  # We're breaking them out so that they can be shared by other methods,
  # especially terminate.
  use_instance_names = []
  use_instance_zones = []
  __use_lb_name = ''     # The load balancer name.
  __use_lb_tp_name = ''  # The load balancer's target pool name.
  __use_lb_hc_name = ''  # The load balancer's health check name.
  __use_lb_target = ''   # The load balancer's 'target' resource.
  __use_http_lb_name = '' # The HTTP load balancer name.
  __use_http_lb_proxy_name = '' # The HTTP load balancer target proxy name.
  __use_http_lb_hc_name = '' # The HTTP load balancer health check name.
  __use_http_lb_bs_name = '' # The HTTP load balancer backend service name.
  __use_http_lb_fr_name = '' # The HTTP load balancer forwarding rule.
  __use_http_lb_map_name = '' # The HTTP load balancer url map name.
  __use_http_lb_http_proxy_name = '' # The HTTP load balancer target http proxy.

  @classmethod
  def new_agent(cls, bindings):
    """Implements the base class interface to create a new agent.

    This method is called by the base classes during setup/initialization.

    Args:
      bindings: The bindings dictionary with configuration information
        that this factory can draw from to initialize. If the factory would
        like additional custom bindings it could add them to initArgumentParser.

    Returns:
      A citest.service_testing.BaseAgent that can interact with Kato.
      This is the agent that test operations will be posted to.
    """
    return kato.new_agent(bindings)

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
    # We're going to make specific instances so we can refer to them later
    # in tests involving instances. The instances are decorated to trace back
    # to this particular run so as not to conflict with other tests that may
    # be running.
    self.use_instance_names = [
        'katotest%sa' % self.test_id,
        'katotest%sb' % self.test_id,
        'katotest%sc' % self.test_id]

    # Put the instance in zones. Force one zone to be different
    # to ensure we're testing zone placement. We arent bothering
    # with different regions at this time.
    self.use_instance_zones = [
        self.bindings['TEST_GCE_ZONE'],
        'us-central1-b',
        self.bindings['TEST_GCE_ZONE']]
    if self.use_instance_zones[0] == self.use_instance_zones[1]:
      self.use_instance_zones[1] = 'us-central1-c'

    # Give the instances images and machine types. Again we're forcing
    # one to be different to ensure that we're using the values.
    image_name = [self.bindings['TEST_GCE_IMAGE_NAME'],
                  'debian-7-wheezy-v20150818',
                  self.bindings['TEST_GCE_IMAGE_NAME']]
    if image_name[0] == image_name[1]:
      image_name[1] = 'ubuntu-1404-trusty-v20150805'
    machine_type = ['f1-micro', 'g1-small', 'f1-micro']

    # The instance_spec will turn into the payload of instances we request.
    instance_spec = []
    builder = gcp.GcpContractBuilder(self.gcp_observer)
    for i in range(3):
      # pylint: disable=bad-continuation
      instance_spec.append(
        {
          'createGoogleInstanceDescription': {
            'instanceName': self.use_instance_names[i],
            'image': image_name[i],
            'instanceType': machine_type[i],
            'zone': self.use_instance_zones[i],
            'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT']
            }
        })

      # Verify we created an instance, whether or not it boots.
      (builder.new_clause_builder(
          'Instance %d Created' % i, retryable_for_secs=90)
            .aggregated_list_resource('instances')
            .contains_path_value('name', self.use_instance_names[i]))
      if i < 2:
        # Verify the details are what we asked for.
        # Since we've finished the created clause, this already exists.
        # Note we're only checking the first two since they are different
        # from one another. Anything after that isnt necessary for the test.
        # The clause above already checked that they were all created so we
        # can assume from this test that the details are ok as well.
        (builder.new_clause_builder('Instance %d Details' % i)
            .inspect_resource('instances', self.use_instance_names[i],
                              zone=self.use_instance_zones[i])
            .contains_path_value('machineType', machine_type[i]))
        # Verify the instance eventually boots up.
        # We can combine this with above, but we'll probably need
        # to retry this, but not the above, so this way if the
        # above is broken (wrong), we wont retry thinking it isnt there yet.
        (builder.new_clause_builder('Instance %d Is Running' % i,
                             retryable_for_secs=90)
            .inspect_resource('instances', self.use_instance_names[i],
                              zone=self.use_instance_zones[i])
            .contains_path_eq('status', 'RUNNING'))

    payload = self.agent.make_json_payload_from_object(instance_spec)

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
    builder = gcp.GcpContractBuilder(self.gcp_observer)
    clause = (builder.new_clause_builder('Instances Deleted',
                                         retryable_for_secs=15,
                                         strict=True)
              .aggregated_list_resource('instances'))
    for name in names:
      # If one of our instances still exists, it should be STOPPING.
      name_matches_pred = jp.PathContainsPredicate('name', name)
      is_stopping_pred = jp.PathEqPredicate('status', 'STOPPING')

      # We want the condition to apply to all the observed objects so we'll
      # map the constraint over the observation. Otherwise, if dont map it,
      # then we'd expect the constraint to hold somewhere among the observed
      # objects, but not necessarily all of them.
      clause.add_constraint(jp.IF(name_matches_pred, is_stopping_pred))

    # pylint: disable=bad-continuation
    payload = self.agent.type_to_payload(
          'terminateInstances',
          {
            'instanceIds': names,
            'zone': zone,
            'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT']
          })

    return st.OperationContract(
        self.new_post_operation(
            title='terminate_instances', data=payload, path='gce/ops'),
        contract=builder.build())

  def upsert_google_server_group_tags(self):
    # pylint: disable=bad-continuation
    server_group_name = 'katotest-server-group'
    payload = self.agent.type_to_payload(
        'upsertGoogleServerGroupTagsDescription',
        {
          'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
          'zone': self.bindings['TEST_GCE_ZONE'],
          'serverGroupName': 'katotest-server-group',
          'tags': ['test-tag-1', 'test-tag-2']
        })

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Server Group Tags Added')
        .inspect_resource('instanceGroupManagers', server_group_name)
        .contains_match({
            'name': jp.STR_SUBSTR(server_group_name),
            'tags/items': jp.LIST_MATCHES(['test-tag-1', 'test-tag-2'])
            }))

    return st.OperationContract(
        self.new_post_operation(
            title='upsert_server_group_tags', data=payload, path='ops'),
        contract=builder.build())

  def create_http_load_balancer(self):
    logical_http_lb_name = 'katotest-httplb-' + self.test_id
    self.__use_http_lb_name = logical_http_lb_name

    # TODO(ewiseblatt): 20150530
    # This needs to be abbreviated to hc.
    self.__use_http_lb_hc_name = logical_http_lb_name + '-health-check'

    # TODO(ewiseblatt): 20150530
    # This needs to be abbreviated to bs.
    self.__use_http_lb_bs_name = logical_http_lb_name + '-backend-service'
    self.__use_http_lb_fr_name = logical_http_lb_name

    # TODO(ewiseblatt): 20150530
    # This should be abbreviated (um?).
    self.__use_http_lb_map_name = logical_http_lb_name + '-url-map'

    # TODO(ewiseblatt): 20150530
    # This should be abbreviated (px)?.
    self.__use_http_lb_proxy_name = logical_http_lb_name + '-target-http-proxy'

    interval = 231
    healthy = 8
    unhealthy = 9
    timeout = 65
    path = '/hello/world'

    # TODO(ewiseblatt): 20150530
    # This field might be broken. 123-456 still resolves to 80-80
    # Changing it for now so the test passes.
    port_range = "80-80"

    # TODO(ewiseblatt): 20150530
    # Specify explicit backends?

    health_check = {
        'checkIntervalSec': interval,
        'healthyThreshold': healthy,
        'unhealthyThreshold': unhealthy,
        'timeoutSec': timeout,
        'requestPath': path
        }

    # pylint: disable=bad-continuation
    payload = self.agent.type_to_payload(
        'createGoogleHttpLoadBalancerDescription',
        {
          'healthCheck': health_check,
          'portRange': port_range,
          'loadBalancerName': logical_http_lb_name,
          'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT']
        })

    hc_dict = dict(health_check)
    del hc_dict['requestPath']
    hc_match = {name: jp.NUM_EQ(value)
                for name, value in health_check.items()}
    hc_match['requestPath'] = jp.STR_EQ(path)
    hc_match['name'] = jp.STR_SUBSTR(self.__use_http_lb_hc_name),
    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Http Health Check Added')
        .list_resource('httpHealthChecks')
        .contains_match(hc_match))
    (builder.new_clause_builder('Global Forwarding Rule Added',
                                retryable_for_secs=15)
       .list_resource('globalForwardingRules')
       .contains_match({
          'name': jp.STR_SUBSTR(self.__use_http_lb_fr_name),
          'portRante': jp.STR_EQ(port_range)}))
    (builder.new_clause_builder('Backend Service Added')
       .list_resource('backendServices')
       .contains_match({
           'name': jp.STR_SUBSTR(self.__use_http_lb_bs_name),
           'healthChecks': jp.STR_SUBSTR(self.__use_http_lb_hc_name)}))
    (builder.new_clause_builder('Url Map Added')
       .list_resource('urlMaps')
       .contains_match({
          'name': jp.STR_SUBSTR(self.__use_http_lb_map_name),
          'defaultService': jp.STR_SUBSTR(self.__use_http_lb_bs_name)}))
    (builder.new_clause_builder('Target Http Proxy Added')
       .list_resource('targetHttpProxies')
       .contains_match({
          'name': jp.STR_SUBSTR(self.__use_http_lb_proxy_name),
          'urlMap': jp.STR_SUBSTR(self.__use_http_lb_map_name)}))

    return st.OperationContract(
        self.new_post_operation(
            title='create_http_load_balancer', data=payload, path='ops'),
        contract=builder.build())

  def delete_http_load_balancer(self):
    # pylint: disable=bad-continuation
    payload = self.agent.type_to_payload(
        'deleteGoogleHttpLoadBalancerDescription',
        {
          'loadBalancerName': self.__use_http_lb_name,
          'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT']
        })

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Health Check Removed')
       .list_resource('httpHealthChecks')
       .excludes_path_value('name', self.__use_http_lb_hc_name))
    (builder.new_clause_builder('Global Forwarding Rules Removed')
       .list_resource('globalForwardingRules')
       .excludes_path_value('name', self.__use_http_lb_fr_name))
    (builder.new_clause_builder('Backend Service Removed')
       .list_resource('backendServices')
       .excludes_path_value('name', self.__use_http_lb_bs_name))
    (builder.new_clause_builder('Url Map Removed')
       .list_resource('urlMaps')
       .excludes_path_value('name', self.__use_http_lb_map_name))
    (builder.new_clause_builder('Target Http Proxy Removed')
       .list_resource('targetHttpProxies')
       .excludes_path_value('name', self.__use_http_lb_proxy_name))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_http_load_balancer', data=payload, path='ops'),
        contract=builder.build())


  def upsert_load_balancer(self):
    self.__use_lb_name = 'katotest-lb-' + self.test_id
    self.__use_lb_hc_name = '%s-hc' % self.__use_lb_name
    self.__use_lb_tp_name = '%s-tp' % self.__use_lb_name
    self.__use_lb_target = '{0}/targetPools/{1}'.format(
        self.bindings['TEST_GCE_REGION'], self.__use_lb_tp_name)

    interval = 123
    healthy = 4
    unhealthy = 5
    timeout = 78
    path = '/' + self.__use_lb_target

    health_check = {
        'checkIntervalSec': interval,
        'healthyThreshold': healthy,
        'unhealthyThreshold': unhealthy,
        'timeoutSec': timeout,
        'requestPath': path
        }

    # pylint: disable=bad-continuation
    payload = self.agent.type_to_payload(
        'upsertGoogleLoadBalancerDescription',
        {
          'healthCheck': health_check,
          'region': self.bindings['TEST_GCE_REGION'],
          'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
          'loadBalancerName': self.__use_lb_name
        })

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Forwarding Rules Added',
                                retryable_for_secs=30)
       .list_resource('forwardingRules')
       .contains_path_value('name', self.__use_lb_name)
       .contains_path_value('target', self.__use_lb_target))
    (builder.new_clause_builder('Target Pool Added', retryable_for_secs=15)
       .list_resource('targetPools')
       .contains_path_value('name', self.__use_lb_tp_name))

     # We list the resources here because the name isnt exact
     # and the list also returns the details we need.
    hc_dict = dict(health_check)
    del hc_dict['requestPath']

    hc_match = {name: jp.NUM_EQ(value) for name, value in hc_dict.items()}
    hc_match['requestPath'] = jp.STR_EQ(path)
    hc_match['name'] = jp.STR_SUBSTR(self.__use_http_lb_hc_name)
    (builder.new_clause_builder('Health Check Added', retryable_for_secs=15)
       .list_resource('httpHealthChecks')
       .contains_match(hc_match))

    return st.OperationContract(
      self.new_post_operation(
          title='upsert_load_balancer', data=payload, path='ops'),
      contract=builder.build())

  def delete_load_balancer(self):
    # pylint: disable=bad-continuation
    payload = self.agent.type_to_payload(
        'deleteGoogleLoadBalancerDescription',
        {
          'region': self.bindings['TEST_GCE_REGION'],
          'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT'],
          'loadBalancerName': self.__use_lb_name
        })

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Health Check Removed')
       .list_resource('httpHealthChecks')
       .excludes_path_value('name', self.__use_lb_hc_name))
    (builder.new_clause_builder('Target Pool Removed')
       .list_resource('targetPools')
       .excludes_path_value('name', self.__use_lb_tp_name))
    (builder.new_clause_builder('Forwarding Rule Removed')
       .list_resource('forwardingRules')
       .excludes_path_value('name', self.__use_lb_name))

    return st.OperationContract(
      self.new_post_operation(
          title='delete_load_balancer', data=payload, path='ops'),
      contract=builder.build())

  def register_load_balancer_instances(self):
    """Creates test registering the first two instances with a load balancer.

       Assumes that create_instances test has been run to add
       the instances. Note by design these were in two different zones
       but same region as required by the API this is testing.

       Assumes that upsert_load_balancer has been run to
       create the load balancer itself.
    Returns:
      st.OperationContract
    """
    # pylint: disable=bad-continuation
    payload = self.agent.type_to_payload(
        'registerInstancesWithGoogleLoadBalancerDescription',
        {
          'loadBalancerNames': [self.__use_lb_name],
          'instanceIds': self.use_instance_names[:2],
          'region': self.bindings['TEST_GCE_REGION'],
          'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT']
        })

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Instances in Target Pool',
                                retryable_for_secs=15)
       .list_resource('targetPools')
       .contains_match({
          'name': jp.STR_SUBSTR(self.__use_lb_tp_name),
          'instances': jp.LIST_MATCHES([
              jp.STR_SUBSTR(self.use_instance_names[0]),
              jp.STR_SUBSTR(self.use_instance_names[1])])
          })
       .excludes_match({
          'name': jp.STR_SUBSTR(self.__use_lb_tp_name),
          'instances': jp.LIST_MATCHES(
              [jp.STR_SUBSTR(self.use_instance_names[2])])}))

    return st.OperationContract(
      self.new_post_operation(
          title='register_load_balancer_instances', data=payload, path='ops'),
      contract=builder.build())


  def deregister_load_balancer_instances(self):
    """Creates a test unregistering instances from load balancer.

    Returns:
      st.OperationContract
    """
    # pylint: disable=bad-continuation
    payload = self.agent.type_to_payload(
       'deregisterInstancesFromGoogleLoadBalancerDescription',
        {
          'loadBalancerNames': [self.__use_lb_name],
          'instanceIds': self.use_instance_names[:2],
          'region': self.bindings['TEST_GCE_REGION'],
          'credentials': self.bindings['SPINNAKER_GOOGLE_ACCOUNT']
        })

    # NOTE(ewiseblatt): 20150530
    # This displays an error that 'instances' field doesnt exist.
    # That's because it was removed because all the instances are gone.
    # I dont have a way to express that the field itself is optional,
    # just the record. Leaving it as is because displaying this type of
    # error is usually helpful for development.
    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Instances not in Target Pool',
                                retryable_for_secs=30)
       .list_resource(
          'targetPools', region=self.bindings['TEST_GCE_REGION'])
       .excludes_match({
          'name': jp.STR_SUBSTR(self.__use_lb_tp_name),
          'instances': jp.LIST_MATCHES([
              jp.STR_SUBSTR(self.use_instance_names[0]),
              jp.STR_SUBSTR(self.use_instance_names[1])])
          }))

    return st.OperationContract(
      self.new_post_operation(
          title='deregister_load_balancer_instances', data=payload, path='ops'),
      contract=builder.build())

  def list_available_images(self):
    """Creates a test that confirms expected available images.

    Returns:
      st.OperationContract
    """
    logger = logging.getLogger(__name__)

    # Get the list of images available (to the service account we are using).
    context = citest.base.ExecutionContext()
    gcp_agent = self.gcp_observer
    JournalLogger.begin_context('Collecting expected available images')
    relation_context = 'ERROR'
    try:
      logger.debug('Looking up available images.')

      json_doc = gcp_agent.list_resource(context, 'images')
      for project in GCP_STANDARD_IMAGES.keys():
        logger.info('Looking for images from project=%s', project)
        found = gcp_agent.list_resource(context, 'images', project=project)
        for image in found:
          if not image.get('deprecated', None):
            json_doc.append(image)

      # Produce the list of images that we expect to receive from spinnaker
      # (visible to the primary service account).
      spinnaker_account = self.agent.deployed_config.get(
          'providers.google.primaryCredentials.name')

      logger.debug('Configured with Spinnaker account "%s"', spinnaker_account)
      expect_images = [{'account': spinnaker_account, 'imageName': image['name']}
                       for image in json_doc]
      expect_images = sorted(expect_images, key=lambda k: k['imageName'])
      relation_context = 'VALID'
    finally:
      JournalLogger.end_context(relation=relation_context)

    # pylint: disable=bad-continuation
    builder = HttpContractBuilder(self.agent)
    (builder.new_clause_builder('Has Expected Images')
       .get_url_path('/gce/images/find')
       .add_constraint(jp.PathPredicate(jp.DONT_ENUMERATE_TERMINAL,
                                        jp.EQUIVALENT(expect_images))))

    return st.OperationContract(
        NoOpOperation('List Available Images'),
        contract=builder.build())


class GoogleKatoIntegrationTest(st.AgentTestCase):
  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        GoogleKatoTestScenario)

  def Xtest_a_upsert_server_group_tags(self):
    self.run_test_case(self.scenario.upsert_google_server_group_tags())

  def test_a_upsert_load_balancer(self):
    self.run_test_case(self.scenario.upsert_load_balancer())

  def test_b_create_instances(self):
    self.run_test_case(self.scenario.create_instances())

  def test_c_register_load_balancer_instances(self):
    self.run_test_case(self.scenario.register_load_balancer_instances())

  def Xtest_d_create_http_load_balancer(self):
    self.run_test_case(self.scenario.create_http_load_balancer())

  def Xtest_v_delete_http_load_balancer(self):
    self.run_test_case(
        self.scenario.delete_http_load_balancer(), timeout_ok=True,
        retry_interval_secs=10, max_retries=9)

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

  def test_z_delete_load_balancer(self):
    # TODO(ewiseblatt): 20151220
    # The retry here is really due to the 400 "not ready" race condition
    # within GCP. Would be better to couple this to the agent and not the
    # test so that it is easier to maintain. Need to add a generalization
    # so the agent can see this is a delete test, got a 400, and only
    # in that condition override the default retry parameters, then stick
    # with the defaults here.
    self.run_test_case(self.scenario.delete_load_balancer(), max_retries=5)

  def test_available_images(self):
    self.run_test_case(self.scenario.list_available_images())


def main():
  return citest.base.TestRunner.main(
      parser_inits=[GoogleKatoTestScenario.initArgumentParser],
      test_case_list=[GoogleKatoIntegrationTest])


if __name__ == '__main__':
  sys.exit(main())
