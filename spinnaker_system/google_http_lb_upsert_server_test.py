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
# include server group tests. This test operates on a L7 Lb with one server
# group attached.

# Standard python modules.
import sys

# Citest modules.
import citest

# Spinnaker modules.
from google_http_lb_upsert_scenario import GoogleHttpLoadBalancerTestScenario
from google_http_lb_upsert_test import GoogleHttpLoadBalancerTest


# pylint: disable=too-many-public-methods
class GoogleHttpLoadBalancerServerTest(GoogleHttpLoadBalancerTest):
  '''Test fixture for Http LB test.
  '''


  @classmethod
  def setUpClass(cls):
    super(GoogleHttpLoadBalancerServerTest, cls).setUpClass()


  @classmethod
  def tearDownClass(cls):
    super(GoogleHttpLoadBalancerServerTest, cls).tearDownClass()


  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
      GoogleHttpLoadBalancerTestScenario)

  def test_f_add_server_group(self):
    self.run_test_case(self.scenario.add_server_group())

  def test_n_delete_server_group(self):
    self.run_test_case(self.scenario.delete_server_group())


def main():
  """Implements the main method running this http lb test."""

  defaults = {
      'TEST_STACK': str(GoogleHttpLoadBalancerTestScenario.DEFAULT_TEST_ID),
      'TEST_APP': ('gcphttplbtest' +
                   GoogleHttpLoadBalancerTestScenario.DEFAULT_TEST_ID)
  }

  return citest.base.TestRunner.main(
      parser_inits=[GoogleHttpLoadBalancerTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[GoogleHttpLoadBalancerServerTest])


if __name__ == '__main__':
  sys.exit(main())
