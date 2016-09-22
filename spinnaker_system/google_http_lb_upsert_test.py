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


# See testable_service/integration_test.py and spinnaker_testing/spinnaker.py
# for more details.
#
# The Http(s) test will be used to (more) thoroughly test the GCP L7 load
# balancer Upsert and Delete commands, and will eventually be expanded to
# include server group tests. This test operates on a L7 Lb with zero server
# groups attached.

# Standard python modules.
from OpenSSL import crypto
import sys

# citest modules.
from citest.base import ExecutionContext
import citest
import citest.gcp_testing as gcp
import citest.service_testing as st

# Spinnaker modules.
from google_http_lb_upsert_scenario import GoogleHttpLoadBalancerTestScenario, \
  SCOPES


# pylint: disable=too-many-public-methods
class GoogleHttpLoadBalancerTest(st.AgentTestCase):
  '''Test fixture for Http LB test.
  '''

  FIRST_CERT = ''
  SECOND_CERT = ''


  @staticmethod
  def make_ssl_cert(name):
    '''Create and return an SslCertificate in dictionary format.
    '''
    key = crypto.PKey()
    key.generate_key(crypto.TYPE_RSA, 2048)
    cert = crypto.X509()
    cert.get_subject().C = "US"
    cert.get_subject().ST = "New York"
    cert.get_subject().L = "New York City"
    cert.get_subject().O = "Google"
    cert.get_subject().OU = "Spinnaker"
    cert.get_subject().CN = "localhost"
    cert.set_serial_number(4096)
    cert.gmtime_adj_notBefore(0)
    cert.gmtime_adj_notAfter(24 * 60 * 60)
    cert.set_issuer(cert.get_subject())
    cert.set_pubkey(key)
    cert.sign(key, 'sha1')

    return {
      'name': name,
      'privateKey': crypto.dump_privatekey(crypto.FILETYPE_PEM, key),
      'certificate': crypto.dump_certificate(crypto.FILETYPE_PEM, cert)
    }


  @classmethod
  def setUpClass(cls):
    runner = citest.base.TestRunner.global_runner()
    bindings = runner.bindings
    cls.FIRST_CERT = 'first-cert-%s' % (bindings['TEST_APP'])
    cls.SECOND_CERT = 'second-cert-%s'  % (bindings['TEST_APP'])
    runner = citest.base.TestRunner.global_runner()
    bindings = runner.bindings
    scenario = runner.get_shared_data(GoogleHttpLoadBalancerTestScenario)
    managed_region = runner.bindings['TEST_GCE_REGION']
    title = 'Check Quota for {0}'.format(scenario.__class__.__name__)

    verify_results = gcp.verify_quota(
      title,
      scenario.gcp_observer,
      project_quota=GoogleHttpLoadBalancerTestScenario.MINIMUM_PROJECT_QUOTA,
      regions=[(managed_region,
                GoogleHttpLoadBalancerTestScenario.MINIMUM_REGION_QUOTA)]
    )
    if not verify_results:
      raise RuntimeError('Insufficient Quota: {0}'.format(verify_results))

    # No predicates against this agent, context is empty.
    context = ExecutionContext()
    compute_agent = (
      gcp.GcpComputeAgent
      .make_agent(scopes=SCOPES,
                  credentials_path=bindings['GCE_CREDENTIALS_PATH']))
    compute_agent.invoke_resource(context,
                                  'insert',
                                  resource_type='sslCertificates',
                                  project=bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'],
                                  body=cls.make_ssl_cert(cls.FIRST_CERT))
    compute_agent.invoke_resource(context,
                                  'insert',
                                  resource_type='sslCertificates',
                                  project=bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'],
                                  body=cls.make_ssl_cert(cls.SECOND_CERT))


  @classmethod
  def tearDownClass(cls):
    runner = citest.base.TestRunner.global_runner()
    bindings = runner.bindings
    context = ExecutionContext()
    compute_agent = (
      gcp.GcpComputeAgent
      .make_agent(scopes=SCOPES,
                  credentials_path=bindings['GCE_CREDENTIALS_PATH']))
    compute_agent.invoke_resource(context,
                                  'delete',
                                  resource_type='sslCertificates',
                                  project=bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'],
                                  sslCertificate=cls.FIRST_CERT)
    compute_agent.invoke_resource(context,
                                  'delete',
                                  resource_type='sslCertificates',
                                  project=bindings['GOOGLE_PRIMARY_MANAGED_PROJECT_ID'],
                                  sslCertificate=cls.SECOND_CERT)


  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        GoogleHttpLoadBalancerTestScenario)

  def test_c_upsert_min_lb(self):
    self.run_test_case(self.scenario.upsert_min_load_balancer())

  def test_d_upsert_full_lb(self):
    self.run_test_case(self.scenario.upsert_full_load_balancer())

  def test_e_add_security_group(self):
    self.run_test_case(self.scenario.add_security_group())

  # Test letters f and n are reserved for another test derived from this one.

  def test_g_change_hc(self):
    self.run_test_case(self.scenario.change_health_check())

  def test_h_change_bs(self):
    self.run_test_case(self.scenario.change_backend_service())

  def test_i_add_host_rule(self):
    self.run_test_case(self.scenario.add_host_rule())

  def test_j_update_host_rule(self):
    self.run_test_case(self.scenario.update_host_rule())

  def test_k_update_port_range(self):
    self.run_test_case(self.scenario.update_port_range())

  def test_l_add_cert(self):
    self.run_test_case(self.scenario.add_cert(self.__class__.FIRST_CERT,
                                              'add cert'))

  def test_m_change_cert(self):
    self.run_test_case(self.scenario.add_cert(self.__class__.SECOND_CERT,
                                              'update cert'))

  # Test letters f and n are reserved for another test derived from this one.

  def test_o_delete_lb(self):
    self.run_test_case(self.scenario.delete_load_balancer())


def main():
  """Implements the main method running this http lb test."""

  defaults = {
      'TEST_STACK': str(GoogleHttpLoadBalancerTestScenario.DEFAULT_TEST_ID),
      'TEST_APP': ('gcphttplbtest'
                   + GoogleHttpLoadBalancerTestScenario.DEFAULT_TEST_ID)
  }

  return citest.base.TestRunner.main(
      parser_inits=[GoogleHttpLoadBalancerTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[GoogleHttpLoadBalancerTest])


if __name__ == '__main__':
  sys.exit(main())
