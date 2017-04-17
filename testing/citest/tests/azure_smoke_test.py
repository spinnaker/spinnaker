# Copyright 2017 Microsoft Inc. All Rights Reserved.
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
Smoke test to see if Spinnaker can interoperate with Microsoft Azure.

----- Documentation to update --------

"""
# Standard python modules.
import sys

# citest modules.
import citest.azure_testing as az
import citest.json_contract as jc
import citest.json_predicate as jp
import citest.service_testing as st
import citest.base

# Spinnaker modules.
import spinnaker_testing as sk
import spinnaker_testing.gate as gate

class AzureSmokeTestScenario(sk.SpinnakerTestScenario):
  """Defines the scenario for the smoke test.

  This scenario defines the different test operations.
  We're going to:
    Create a Spinnaker Application
    Create a Spinnaker Security Group
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
    super(AzureSmokeTestScenario, self).__init__(bindings, agent)
    bindings = self.bindings

    # We'll call out the app name because it is widely used
    # because it scopes the context of our activities.
    # pylint: disable=invalid-name
    self.TEST_APP = bindings['TEST_APP']
    self.TEST_STACK = bindings['TEST_STACK']
    self.TEST_SECURITY_GROUP = 'sec_grp_'+ bindings['TEST_APP']
    self.TEST_SECURITY_GROUP_RG = self.TEST_APP+ '-' + self.bindings['TEST_AZURE_RG_LOCATION']


  def create_app(self):
    """Creates OperationContract that creates a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_create_app_operation(
            bindings=self.bindings, application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_AZURE_ACCOUNT']),
        contract=contract)

  def delete_app(self):
    """Creates OperationContract that deletes a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_delete_app_operation(
            application=self.TEST_APP,
            account_name=self.bindings['SPINNAKER_AZURE_ACCOUNT']),
        contract=contract)

  def create_a_security_group(self):
    """Creates OsContract for createServerGroup.

    To verify the operation, we just check that the spinnaker security group
    for the given application was created.
    """
    rules = [
        {
            "access": "Allow",
            "destinationAddressPrefix": "*",
            "destinationPortRange": "80-80",
            "direction": "InBound",
            "endPort": 80,
            "name": self.TEST_SECURITY_GROUP,
            "priority": 100,
            "protocol": "tcp",
            "sourceAddressPrefix": "*",
            "sourcePortRange": "*",
            "startPort": 80
        }]
    job = [{
        "provider": "azure",
        "application": self.TEST_APP,
        "appName": self.TEST_APP,
        "region": self.bindings['TEST_AZURE_RG_LOCATION'],
        "stack": self.TEST_STACK,
        "description": "Test - create security group for {app}".format(
            app=self.TEST_APP),
        "detail": "",
        "credentials": self.bindings['SPINNAKER_AZURE_ACCOUNT'],
        "securityRules": rules,
        "name": self.TEST_SECURITY_GROUP,
        "securityGroupName": self.TEST_SECURITY_GROUP,
        "cloudProvider": "azure",
        "type": "upsertSecurityGroup",
        "user": "[anonymous]"
    }]
    builder = az.AzContractBuilder(self.az_observer)
    (builder.new_clause_builder(
        'Security Group Created', retryable_for_secs=30)
     .collect_resources(
         az_resource='network',
         command='nsg',
         args=['show', '--name', self.TEST_SECURITY_GROUP, '--resource-group', self.TEST_SECURITY_GROUP_RG])
     .contains_pred_list([
         jp.DICT_MATCHES({
             'name': jp.STR_SUBSTR(self.TEST_SECURITY_GROUP)#,
            #  'securityRules': jp.STR_SUBSTR("protocol='tcp'")
            #           and jp.STR_SUBSTR("priority=100")
            #           and jp.STR_SUBSTR("destinationPortRange='80-80'")})
         })]))

    payload = self.agent.make_json_payload_from_kwargs(
        job=job, description=' Test - create security group for {app}'.format(
            app=self.TEST_APP),
        application=self.TEST_APP)

    return st.OperationContract(
        self.new_post_operation(
            title='create_security_group', data=payload,
            path='applications/{app}/tasks'.format(app=self.TEST_APP)),
        contract=builder.build())

  def delete_a_security_group(self):
    """Creates azContract for deleteServerGroup.

    To verify the operation, we just check that the spinnaker security group
    for the given application was deleted.
    """
    #Get ID of the created security group
    az_agent = az.AzAgent(None)
    data = az_agent.get_resource('security group', self.TEST_SECURITY_GROUP)
    security_group_id = data['id']

    payload = self.agent.make_json_payload_from_kwargs(
        job=[{
            "Provider": "openstack",
            "id": security_group_id,
            "region": self.bindings['AZ_REGION_NAME'],
            "regions": [self.bindings['AZ_REGION_NAME']],
            "account": self.bindings['SPINNAKER_AZ_ACCOUNT'],
            "securityGroupName": self.TEST_SECURITY_GROUP,
            "cloudProvider": "openstack",
            "type": "deleteSecurityGroup",
            "user": self.bindings['TEST_AZ_USERNAME']
        }],
        application=self.TEST_APP,
        description='Delete Security Group: : ' + self.TEST_SECURITY_GROUP)

    builder = az.AzContractBuilder(self.az_observer)
    (builder.new_clause_builder(
        'Security Group Deleted', retryable_for_secs=30)
     .show_resource('security group', self.TEST_SECURITY_GROUP,
                    no_resources_ok=True)
     .excludes_path_eq('name', self.TEST_SECURITY_GROUP)
    )
    return st.OperationContract(
        self.new_post_operation(
            title='delete_security_group', data=payload,
            path='applications/{app}/tasks'.format(app=self.TEST_APP)),
        contract=builder.build())

class AzureSmokeTest(st.AgentTestCase):
  """The test fixture for the AzureSmokeTest.

  This is implemented using citest OperationContract instances that are
  created by the AzureSmokeTestScenario.
  """
  # pylint: disable=missing-docstring
  @property
  def scenario(self):
    return citest.base.TestRunner.global_runner().get_shared_data(
        AzureSmokeTestScenario)

  def test_a_create_app(self):
    self.run_test_case(self.scenario.create_app())

  def test_b_create_security_group(self):
    self.run_test_case(self.scenario.create_a_security_group())

  def test_y_delete_security_group(self):
    self.run_test_case(self.scenario.delete_a_security_group(),
                       retry_interval_secs=8, max_retries=8)

  def test_z_delete_app(self):
    self.run_test_case(self.scenario.delete_app(),
                       retry_interval_secs=8, max_retries=8)

def main():
  """Implements the main method running this smoke test."""

  defaults = {
      'TEST_STACK': str(AzureSmokeTestScenario.DEFAULT_TEST_ID),
      'TEST_APP': 'azure-smoketest' + AzureSmokeTestScenario.DEFAULT_TEST_ID
      }

  return citest.base.TestRunner.main(
      parser_inits=[AzureSmokeTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[AzureSmokeTest])

if __name__ == '__main__':
  sys.exit(main())

