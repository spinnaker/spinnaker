# Copyright 2016 Google Inc. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the 'License');
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an 'AS IS' BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Test scenario for Gcp Http(s) Load Balancers.

# Standard python modules.
import copy
import json
import time

# citest modules.
import citest.gcp_testing as gcp
import citest.json_predicate as jp
import citest.service_testing as st

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate

SCOPES = [gcp.COMPUTE_READ_WRITE_SCOPE]
GCE_URL_PREFIX = 'https://www.googleapis.com/compute/v1/projects/'

class GoogleHttpLoadBalancerTestScenario(sk.SpinnakerTestScenario):
  '''Defines the tests for L7 Load Balancers.
  '''

  MINIMUM_PROJECT_QUOTA = {
    'INSTANCE_TEMPLATES': 1,
    'BACKEND_SERVICES': 3,
    'URL_MAPS': 1,
    'HEALTH_CHECKS': 1,
    'IN_USE_ADDRESSES': 2,
    'SSL_CERTIFICATES': 2,
    'TARGET_HTTP_PROXIES': 1,
    'TARGET_HTTPS_PROXIES': 1,
    'FORWARDING_RULES': 2
  }

  MINIMUM_REGION_QUOTA = {
      'CPUS': 2,
      'IN_USE_ADDRESSES': 2,
      'INSTANCE_GROUP_MANAGERS': 1,
      'INSTANCES': 2,
  }

  @classmethod
  def new_agent(cls, bindings):
    '''Implements citest.service_testing.AgentTestScenario.new_agent.'''
    agent = gate.new_agent(bindings)
    agent.default_max_wait_secs = 600
    return agent


  def __init__(self, bindings, agent=None):
    '''Constructor.

    Args:
      bindings: [dict] The data bindings to use to configure the scenario.
      agent: [GateAgent] The agent for invoking the test operations on Gate.
    '''
    super(GoogleHttpLoadBalancerTestScenario, self).__init__(bindings, agent)

    bindings = self.bindings

    self.__lb_detail = 'lb'
    self.TEST_APP = bindings['TEST_APP']
    self.__lb_name = '{app}-{stack}-{detail}'.format(
        app=bindings['TEST_APP'], stack=bindings['TEST_STACK'],
        detail=self.__lb_detail)
    self.__proto_hc = {
      'name': 'basic-http-check',
      'requestPath': '/',
      'port': 80,
      'checkIntervalSec': 2,
      'timeoutSec': 1,
      'healthyThreshold': 3,
      'unhealthyThreshold': 4
    }
    self.__proto_upsert = {
      'cloudProvider': 'gce',
      'provider': 'gce',
      'stack': bindings['TEST_STACK'],
      'credentials': bindings['GCE_CREDENTIALS'],
      'region': bindings['TEST_GCE_REGION'],
      'loadBalancerType': 'HTTP',
      'loadBalancerName': self.__lb_name,
      'portRange': '80',
      'defaultService': {
        'name': 'default-backend-service',
        'backends': [],
        'healthCheck': self.__proto_hc,
      },
      'certificate': '',
      'hostRules': [
        {
          'hostPatterns': ['host1.com', 'host2.com'],
          'pathMatcher': {
            'pathRules': [
              {
                'paths': ['/path', '/path2/more'],
                'backendService': {
                  'name': 'backend-service',
                  'backends': [],
                  'healthCheck': self.__proto_hc,
                }
              }
            ],
            'defaultService': {
              'name': 'pm-backend-service',
              'backends': [],
              'healthCheck': self.__proto_hc,
            }
          }
        }
      ],
      'type': 'upsertLoadBalancer',
      'availabilityZones': {bindings['TEST_GCE_REGION']: []},
      'user': '[anonymous]'
    }


  def _get_bs_link(self, bs):
    '''Make a fully-formatted backend service link.
    '''
    return (GCE_URL_PREFIX
            + self.bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID']
            + '/global/backendServices/' + bs)


  def _get_hc_link(self, hc):
    '''Make a fully-formatted health check link.
    '''
    return (GCE_URL_PREFIX
            + self.bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID']
            + '/global/httpHealthChecks/' + hc)


  def _set_all_hcs(self, upsert, hc):
    '''Set all health checks in upsert to hc.
    '''
    upsert['defaultService']['healthCheck'] = hc
    for host_rule in upsert['hostRules']:
      path_matcher = host_rule['pathMatcher']
      path_matcher['defaultService']['healthCheck'] = hc
      for path_rule in path_matcher['pathRules']:
        path_rule['backendService']['healthCheck'] = hc


  def _add_contract_clauses(self, contract_builder, upsert):
    '''Add the proper predicates to the contract builder for a given
    upsert description.
    '''
    host_rules = upsert['hostRules'] # Host rules will be distinct.
    backend_services = [upsert['defaultService']]
    for host_rule in host_rules:
      path_matcher = host_rule['pathMatcher']
      backend_services.append(path_matcher['defaultService'])
      for path_rule in path_matcher['pathRules']:
        backend_services.append(path_rule['backendService'])
    health_checks = [service['healthCheck'] for service in backend_services]

    hc_clause_builder = (contract_builder
                         .new_clause_builder('Health Checks Created',
                                             retryable_for_secs=30)
                         .list_resource('httpHealthChecks'))
    for hc in health_checks:
      hc_clause_builder.contains_pred_list(
        [
          jp.PathEqPredicate('name', hc['name']),
          jp.PathEqPredicate('requestPath', hc['requestPath']),
          jp.PathEqPredicate('port', hc['port'])
        ]
      )

    bs_clause_builder = (contract_builder.
                         new_clause_builder('Backend Services Created',
                                            retryable_for_secs=30).
                         list_resource('backendServices'))
    for bs in backend_services:
      bs_clause_builder.contains_pred_list(
        [
         jp.PathEqPredicate('name', bs['name']),
         jp.PathEqPredicate('portName', 'http'),
         jp.PathContainsPredicate('healthChecks[0]',
                                  self._get_hc_link(bs['healthCheck']['name']))
        ]
      )

    url_map_clause_builder = (contract_builder
                              .new_clause_builder('Url Map Created',
                                                  retryable_for_secs=30)
                              .list_resource('urlMaps'))
    for hr in host_rules:
      pred_list = []
      pm = hr['pathMatcher']
      pred_list.append(jp.AND([
        jp.PathEqPredicate('name', self.__lb_name),
        jp.PathEqPredicate('defaultService',
                           self._get_bs_link(upsert['defaultService']['name'])),
        jp.PathContainsPredicate(
          'pathMatchers/defaultService',
          self._get_bs_link(pm['defaultService']['name'])),
      ]))
      pred_list.append(
        jp.AND([jp.PathContainsPredicate('hostRules/hosts',
                                         host) for host in hr['hostPatterns']])
      )
      for pr in pm['pathRules']:
        pred_list.append(
         jp.PathContainsPredicate(
           'pathMatchers/pathRules/service',
           self._get_bs_link(pr['backendService']['name'])),
        )
        for path in pr['paths']:
          pred_list.append(
            jp.PathContainsPredicate('pathMatchers/pathRules/paths', path),
          )
      url_map_clause_builder.contains_pred_list(pred_list)

    port_string = '443-443'
    if upsert['certificate'] == '':
      port_string = '%s-%s' % (upsert['portRange'], upsert['portRange'])

    (contract_builder.new_clause_builder('Forwarding Rule Created',
                                         retryable_for_secs=30)
     .list_resource('globalForwardingRules')
     .contains_pred_list(
       [
         jp.PathEqPredicate('name', self.__lb_name),
         jp.PathEqPredicate('portRange', port_string)
       ]))

    proxy_clause_builder = contract_builder.new_clause_builder(
      'Target Proxy Created', retryable_for_secs=30)
    self._add_proxy_clause(upsert['certificate'], proxy_clause_builder)


  def _add_proxy_clause(self, certificate, proxy_clause_builder):
    target_proxy_name = '%s-target-%s-proxy'
    if certificate:
      target_proxy_name = target_proxy_name % (self.__lb_name, 'https')
      (proxy_clause_builder.list_resource('targetHttpsProxies')
       .contains_pred_list([jp.PathEqPredicate('name', target_proxy_name)]))
    else:
      target_proxy_name = target_proxy_name % (self.__lb_name, 'http')
      (proxy_clause_builder.list_resource('targetHttpProxies')
       .contains_pred_list([jp.PathEqPredicate('name', target_proxy_name)]))


  def upsert_full_load_balancer(self):
    '''Upserts L7 LB with full hostRules, pathMatchers, etc.

    Calls the upsertLoadBalancer operation with a payload, then verifies that
    the expected resources are visible on GCP.
    '''
    hc = copy.deepcopy(self.__proto_hc)
    hc['requestPath'] = '/'
    hc['port'] = 80
    upsert = copy.deepcopy(self.__proto_upsert)
    self._set_all_hcs(upsert, hc)

    payload = self.agent.make_json_payload_from_kwargs(
      job=[upsert],
      description='Upsert L7 Load Balancer: ' + self.__lb_name,
      application=self.TEST_APP
    )

    contract_builder = gcp.GcpContractBuilder(self.gcp_observer)
    self._add_contract_clauses(contract_builder, upsert)

    return st.OperationContract(
      self.new_post_operation(title='upsert full http lb',
                              data=payload, path='tasks'),
      contract=contract_builder.build()
    )


  def upsert_min_load_balancer(self):
    '''Upserts a L7 LB with the minimum description.
    '''
    upsert = copy.deepcopy(self.__proto_upsert)
    upsert['hostRules'] = []

    payload = self.agent.make_json_payload_from_kwargs(
      job=[upsert],
      description='Upsert L7 Load Balancer: ' + self.__lb_name,
      application=self.TEST_APP
    )

    contract_builder = gcp.GcpContractBuilder(self.gcp_observer)
    self._add_contract_clauses(contract_builder, upsert)

    return st.OperationContract(
      self.new_post_operation(title='upsert min http lb',
                              data=payload, path='tasks'),
      contract=contract_builder.build()
    )


  def delete_load_balancer(self):
    '''Deletes the L7 LB.
    '''
    bindings = self.bindings
    delete = {
      'type': 'deleteLoadBalancer',
      'cloudProvider': 'gce',
      'loadBalancerType': 'HTTP',
      'loadBalancerName': self.__lb_name,
      'region': bindings['TEST_GCE_REGION'],
      'regions': [bindings['TEST_GCE_REGION']],
      'credentials': bindings['GCE_CREDENTIALS'],
      'user': '[anonymous]'
    }

    payload = self.agent.make_json_payload_from_kwargs(
      job=[delete],
      description='Delete L7 Load Balancer: {0} in {1}:{2}'.format(
        self.__lb_name,
        bindings['GCE_CREDENTIALS'],
        bindings['TEST_GCE_REGION'],
      ),
      application=self.TEST_APP
    )
    contract_builder = gcp.GcpContractBuilder(self.gcp_observer)
    (contract_builder.new_clause_builder('Health Check Removed',
                                         retryable_for_secs=30)
     .list_resource('httpHealthChecks')
     .excludes_path_value('name', self.__proto_hc['name'])
    )
    (contract_builder.new_clause_builder('Url Map Removed',
                                         retryable_for_secs=30)
     .list_resource('urlMaps')
     .excludes_path_value('name', self.__lb_name)
    )
    (contract_builder.new_clause_builder('Forwarding Rule Removed',
                                         retryable_for_secs=30)
     .list_resource('globalForwardingRules')
     .excludes_path_value('name', self.__lb_name)
    )

    return st.OperationContract(
      self.new_post_operation(
        title='delete_load_balancer', data=payload, path='tasks'),
      contract=contract_builder.build())


  def change_health_check(self):
    '''Changes the health check associated with the LB.
    '''
    upsert = copy.deepcopy(self.__proto_upsert)
    hc = copy.deepcopy(self.__proto_hc)
    hc['requestPath'] = '/changedPath'
    hc['port'] = 8080
    self._set_all_hcs(upsert, hc)

    payload = self.agent.make_json_payload_from_kwargs(
      job=[upsert],
      description='Upsert L7 Load Balancer: ' + self.__lb_name,
      application=self.TEST_APP
    )

    contract_builder = gcp.GcpContractBuilder(self.gcp_observer)
    self._add_contract_clauses(contract_builder, upsert)

    return st.OperationContract(
      self.new_post_operation(title='change health checks',
                              data=payload, path='tasks'),
      contract=contract_builder.build()
    )


  def change_backend_service(self):
    '''Changes the default backend service associated with the LB.
    '''
    hc = copy.deepcopy(self.__proto_hc)
    bs_upsert = copy.deepcopy(self.__proto_upsert)
    hc['name'] = 'updated-hc'
    hc['requestPath'] = '/changedPath1'
    hc['port'] = 8080

    bs_upsert['defaultService']['healthCheck'] = hc
    payload = self.agent.make_json_payload_from_kwargs(
      job=[bs_upsert],
      description='Upsert L7 Load Balancer: ' + self.__lb_name,
      application=self.TEST_APP
    )

    contract_builder = gcp.GcpContractBuilder(self.gcp_observer)
    self._add_contract_clauses(contract_builder, bs_upsert)

    return st.OperationContract(
      self.new_post_operation(title='change backend services',
                              data=payload, path='tasks'),
      contract=contract_builder.build()
    )


  def add_host_rule(self):
    '''Adds a host rule to the url map.
    '''
    bs_upsert = copy.deepcopy(self.__proto_upsert)
    hr = copy.deepcopy(bs_upsert['hostRules'][0])
    hr['hostPatterns'] = ['added.host1.com', 'added.host2.com']
    hr['pathMatcher']['pathRules'][0]['paths'] = ['/added/path']
    bs_upsert['hostRules'].append(hr)

    payload = self.agent.make_json_payload_from_kwargs(
      job=[bs_upsert],
      description='Upsert L7 Load Balancer: ' + self.__lb_name,
      application=self.TEST_APP
    )

    contract_builder = gcp.GcpContractBuilder(self.gcp_observer)
    self._add_contract_clauses(contract_builder, bs_upsert)

    return st.OperationContract(
      self.new_post_operation(title='add host rule',
                              data=payload, path='tasks'),
      contract=contract_builder.build()
    )


  def update_host_rule(self):
    '''Updates a host rule to the url map.
    '''
    bs_upsert = copy.deepcopy(self.__proto_upsert)
    hr = copy.deepcopy(bs_upsert['hostRules'][0])
    hr['hostPatterns'] = ['updated.host1.com']
    hr['pathMatcher']['pathRules'][0]['paths'] = ['/updated/path']
    bs_upsert['hostRules'].append(hr)

    payload = self.agent.make_json_payload_from_kwargs(
      job=[bs_upsert],
      description='Upsert L7 Load Balancer: ' + self.__lb_name,
      application=self.TEST_APP
    )

    contract_builder = gcp.GcpContractBuilder(self.gcp_observer)
    self._add_contract_clauses(contract_builder, bs_upsert)

    return st.OperationContract(
      self.new_post_operation(title='update host rule',
                              data=payload, path='tasks'),
      contract=contract_builder.build()
    )


  def update_port_range(self):
    '''Updates the port range on the forwarding rule.
    '''
    bs_upsert = copy.deepcopy(self.__proto_upsert)
    bs_upsert['portRange'] = '8080'

    payload = self.agent.make_json_payload_from_kwargs(
      job=[bs_upsert],
      description='Upsert L7 Load Balancer: ' + self.__lb_name,
      application=self.TEST_APP
    )

    contract_builder = gcp.GcpContractBuilder(self.gcp_observer)
    self._add_contract_clauses(contract_builder, bs_upsert)

    return st.OperationContract(
      self.new_post_operation(title='update port range',
                              data=payload, path='tasks'),
      contract=contract_builder.build()
    )


  def add_cert(self, certname, title):
    '''Add cert to targetHttpProxy to make it a targetHttpsProxy.
    '''
    bs_upsert = copy.deepcopy(self.__proto_upsert)
    bs_upsert['certificate'] = certname

    payload = self.agent.make_json_payload_from_kwargs(
      job=[bs_upsert],
      description='Upsert L7 Load Balancer: ' + self.__lb_name,
      application=self.TEST_APP
    )

    contract_builder = gcp.GcpContractBuilder(self.gcp_observer)
    self._add_contract_clauses(contract_builder, bs_upsert)

    return st.OperationContract(
      self.new_post_operation(title=title,
                              data=payload, path='tasks'),
      contract=contract_builder.build()
    )


  def add_security_group(self):
    '''Associates a security group with the L7 load balancer.
    '''
    bindings = self.bindings
    sec_group_payload = self.agent.make_json_payload_from_kwargs(
      job=[
        {
          'allowed': [
            {
              'ipProtocol': 'tcp',
              'portRanges': ['80-80']
            },
            {
              'ipProtocol': 'tcp',
              'portRanges': ['8080-8080']
            },
            {
              'ipProtocol': 'tcp',
              'portRanges': ['443-443']
            }
          ],
          'backingData': {'networks': ['default']},
          'cloudProvider': 'gce',
          'application': self.TEST_APP,
          'credentials': bindings['GCE_CREDENTIALS'],
          'description': '',
          'detail': 'http',
          'ipIngress': [
            {
              'type': 'tcp',
              'startPort': 80,
              'endPort': 80,
            },
            {
              'type': 'tcp',
              'startPort': 8080,
              'endPort': 8080,
            },
            {
              'type': 'tcp',
              'startPort': 443,
              'endPort': 443,
            }
          ],
          'name': self.__lb_name + '-rule',
          'network': 'default',
          'region': 'global',
          'securityGroupName': self.__lb_name + '-rule',
          'sourceRanges': ['0.0.0.0/0'],
          'type': 'upsertSecurityGroup',
          'user': '[anonymous]'
        }
      ],
      description='Create a Security Group for L7 operations.',
      application=self.TEST_APP
    )
    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Security Group Created',
                                retryable_for_secs=30)
     .list_resource('firewalls')
     .contains_pred_list(
       [
         jp.PathContainsPredicate('name', self.__lb_name + '-rule')
       ]
     )
    )

    return st.OperationContract(
      self.new_post_operation(title='create security group',
                              data=sec_group_payload, path='tasks'),
      contract=builder.build()
    )

  def add_server_group(self):
    '''Adds a server group to the L7 LB.
    '''
    time.sleep(60) # Wait for the L7 LB to be ready.
    bindings = self.bindings
    group_name = '{app}-{stack}-v000'.format(app=self.TEST_APP,
                                             stack=bindings['TEST_STACK'])
    policy = {
      'balancingMode': 'UTILIZATION',
      'listeningPort': 80,
      'maxUtilization': 0.8,
      'capacityScaler': 0.8
    }

    payload = self.agent.make_json_payload_from_kwargs(
      job=[{
        'cloudProvider': 'gce',
        'application': self.TEST_APP,
        'credentials': bindings['GCE_CREDENTIALS'],
        'strategy':'',
        'capacity': {'min':1, 'max':1, 'desired':1},
        'targetSize': 1,
        'image': bindings['TEST_GCE_IMAGE_NAME'],
        'zone': bindings['TEST_GCE_ZONE'],
        'stack': bindings['TEST_STACK'],
        'instanceType': 'f1-micro',
        'type': 'createServerGroup',
        'securityGroups': [self.__lb_name + '-rule'],
        'loadBalancers': [self.__lb_name],
        'backendServices': {self.__lb_name: ['backend-service']},
        'disableTraffic': False,
        'loadBalancingPolicy': {
          'balancingMode': 'UTILIZATION',
          'listeningPort': 80,
          'maxUtilization': 0.8,
          'capacityScaler': 0.8
        },
        'availabilityZones': {
          bindings['TEST_GCE_REGION']: [bindings['TEST_GCE_ZONE']]
        },
        'instanceMetadata': {
          'startup-script': ('sudo apt-get update'
                             ' && sudo apt-get install apache2 -y'),
          'global-load-balancer-names': self.__lb_name,
          'backend-service-names': 'backend-service',
          'load-balancing-policy': json.dumps(policy)
        },
        'account': bindings['GCE_CREDENTIALS'],
        'authScopes': ['compute'],
        'user': '[anonymous]'
      }],
      description='Create Server Group in ' + group_name,
      application=self.TEST_APP
    )

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Managed Instance Group Added',
                                retryable_for_secs=30)
     .inspect_resource('instanceGroupManagers', group_name)
     .contains_path_eq('targetSize', 1)
    )

    return st.OperationContract(
      self.new_post_operation(title='create server group',
                              data=payload, path='tasks'),
      contract=builder.build()
    )


  def delete_server_group(self):
    """Creates OperationContract for deleteServerGroup.

    To verify the operation, we just check that the GCP managed instance group
    is no longer visible on GCP (or is in the process of terminating).
    """
    bindings = self.bindings
    group_name = '{app}-{stack}-v000'.format(
        app=self.TEST_APP, stack=bindings['TEST_STACK'])

    payload = self.agent.make_json_payload_from_kwargs(
      job=[{
        'cloudProvider': 'gce',
        'serverGroupName': group_name,
        'region': bindings['TEST_GCE_REGION'],
        'zone': bindings['TEST_GCE_ZONE'],
        'asgName': group_name,
        'type': 'destroyServerGroup',
        'regions': [bindings['TEST_GCE_REGION']],
        'zones': [bindings['TEST_GCE_ZONE']],
        'credentials': bindings['GCE_CREDENTIALS'],
        'user': '[anonymous]'
      }],
      application=self.TEST_APP,
      description='DestroyServerGroup: ' + group_name
    )

    builder = gcp.GcpContractBuilder(self.gcp_observer)
    (builder.new_clause_builder('Managed Instance Group Removed')
     .inspect_resource('instanceGroupManagers', group_name,
                       no_resource_ok=True)
     .contains_path_eq('targetSize', 0))

    (builder.new_clause_builder('Instances Are Removed',
                                retryable_for_secs=30)
     .list_resource('instances')
     .excludes_path_value('name', group_name))

    return st.OperationContract(
      self.new_post_operation(
        title='delete server group', data=payload, path='tasks'),
      contract=builder.build()
    )
