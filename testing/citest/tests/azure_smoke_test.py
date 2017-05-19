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

Sample Usage:
    Assuming you have created $CITEST_ROOT points to the root directory of this repository
    (which is . if you execute this from the root)

  PYTHONPATH=$CITEST_ROOT:$CITEST_ROOT/spinnaker \
    python $CITEST_ROOT/spinnaker/azure_smoke_test.py \
    "--native_hostname=localhost", \
    "--host_platform=native", \
    "--test_azure_rg_location=$TEST_AZURE_RG_LOCATION", \
    "--azure_storage_account_name=$AZURE_STORAGE_ACCOUNT_NAME", \
    "--azure_storage_account_key=$AZURE_STORAGE_ACCOUNT_KEY", \        

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

    # create default sec grp name  
    # Spinnaker-azure appends region to actual RG used by an app to support multi-region
    # pylint: disable=invalid-name
    self.TEST_SECURITY_GROUP = 'sec_grp_' + self.bindings['TEST_APP']
    self.TEST_SECURITY_GROUP_RULE_1=self.TEST_SECURITY_GROUP + '_RULE_1'
    self.TEST_SECURITY_GROUP_RG = self.bindings['TEST_APP'] + '-' + self.bindings['TEST_AZURE_RG_LOCATION']

  #TODO: Perform the login to Azure withTEST_APP the SPN passed as parameters

  def create_app(self):
    """Creates OperationContract that creates a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_create_app_operation(
            bindings=self.bindings, application=self.bindings['TEST_APP'],
            account_name=self.bindings['SPINNAKER_AZURE_ACCOUNT']),
        contract=contract)

  def delete_app(self):
    """Creates OperationContract that deletes a new Spinnaker Application."""
    contract = jc.Contract()
    return st.OperationContract(
        self.agent.make_delete_app_operation(
            application=self.bindings['TEST_APP'],
            account_name=self.bindings['SPINNAKER_AZURE_ACCOUNT']),
        contract=contract)

  def old_create_app(self):
    """Creates OperationContract that creates a new Spinnaker Application and validate its creation in Azure Storage.
    """
    email = self.bindings.get('TEST_EMAIL', 'testuser@testhost.org')
    payload = self.agent.make_json_payload_from_kwargs(
            job=[{
                'type': 'createApplication',
                'account': self.bindings['SPINNAKER_AZURE_ACCOUNT'],
                'application': {
                    'name': self.bindings['TEST_APP'],
                    'description': 'Gate Testing Application for Azure',
                    'email': email
                },
                'user': '[anonymous]'
            }],
            description= 'Test - create application {app}'.format(app=self.bindings['TEST_APP']),
            application=self.bindings['TEST_APP'])

    builder = az.AzContractBuilder(self.az_observer)
    (builder.new_clause_builder(
        'Application Created', retryable_for_secs=30)
      .collect_resources(
          az_resource='storage',
          command='blob',
          args=['exists', '--container-name', 'front50',
          '--name', 'applications/'+self.bindings['TEST_APP']+'/application-metadata.json',
          '--account-name', self.bindings['azure_storage_account_name'],
          '--account-key', self.bindings['spinnaker_azure_storage_account_key']])
      .contains_path_eq('exists',True))

    return st.OperationContract(
        self.new_post_operation(
            title='create_app', data=payload,
            path='tasks'),
        contract=builder.build())

  def old_delete_app(self):
    """Creates OperationContract that deletes a new Spinnaker Application and validates its deletion in Azure Storage.
    """

    payload = self.agent.make_json_payload_from_kwargs(
            job=[{
                'type': 'deleteApplication',
                'account': self.bindings['SPINNAKER_AZURE_ACCOUNT'],
                'application': {
                    'name': self.bindings['TEST_APP']
                },
                'user': '[anonymous]'
            }],
            description= 'Test - delete application {app}'.format(app=self.bindings['TEST_APP']),
            application=self.bindings['TEST_APP'])

    builder = az.AzContractBuilder(self.az_observer)
    (builder.new_clause_builder(
        'Application Created', retryable_for_secs=30)
      .collect_resources(
          az_resource='storage',
          command='blob',
          args=['exists', '--container-name', 'front50',
          '--name', 'applications/'+self.bindings['TEST_APP']+'/application-metadata.json',
          '--account-name', self.bindings['azure_storage_account_name'],
          '--account-key', self.bindings['spinnaker_azure_storage_account_key']])
      .contains_path_eq('exists',False))

    return st.OperationContract(
        self.new_post_operation(
            title='delete_app', data=payload,
            path='tasks'),
        contract=builder.build())


  def create_a_security_group(self):
    """Creates AzContract for createServerGroup.

    To verify the operation, we just check that the spinnaker security group
    for the given application was created.
    This will create a Network Security Group in a Resource Group on your azure Subscription
    """
    rules = [
        {
            "access": "Allow",
            "destinationAddressPrefix": "*",
            "destinationPortRange": "80-80",
            "direction": "InBound",
            "endPort": 80,
            "name": self.TEST_SECURITY_GROUP_RULE_1,
            "priority": 100,
            "protocol": "tcp",
            "sourceAddressPrefix": "*",
            "sourcePortRange": "*",
            "startPort": 80
        }]
    job = [{
        "provider": "azure",
        "application": self.bindings['TEST_APP'],
        "appName": self.bindings['TEST_APP'],
        "region": self.bindings['TEST_AZURE_RG_LOCATION'],
        "stack": self.bindings['TEST_STACK'],
        "description": "Test - create security group for {app}".format(
            app=self.bindings['TEST_APP']),
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
         args=['show', '--name', self.TEST_SECURITY_GROUP, '--resource-group', self.TEST_SECURITY_GROUP_RG],
         no_resources_ok=True)
    #sec grp name matches expected
    .contains_match({'name': jp.STR_SUBSTR(self.TEST_SECURITY_GROUP)})
    .contains_path_pred(
        'securityRules', jp.LIST_MATCHES([
            jp.DICT_MATCHES({
                'protocol': jp.STR_EQ('tcp'),
                'name': jp.STR_EQ(self.TEST_SECURITY_GROUP_RULE_1)})],
            strict=True),
            enumerate_terminals=False
    ))

    payload = self.agent.make_json_payload_from_kwargs(
        job=job, description=' Test - create security group for {app}'.format(
            app=self.bindings['TEST_APP']),
        application=self.bindings['TEST_APP'])

    return st.OperationContract(
        self.new_post_operation(
            title='create_security_group', data=payload,
            path='applications/{app}/tasks'.format(app=self.bindings['TEST_APP'])),
        contract=builder.build())

  def delete_a_security_group(self):
    """Creates azContract for deleteServerGroup.

    To verify the operation, we just check that the spinnaker security group
    for the given application was deleted.
    """

    payload = self.agent.make_json_payload_from_kwargs(
        job = [{
            "Provider": "azure",
            "appName": self.bindings['TEST_APP'],
            "region": self.bindings['TEST_AZURE_RG_LOCATION'],
            "regions": [self.bindings['TEST_AZURE_RG_LOCATION']],
            "credentials": self.bindings['SPINNAKER_AZURE_ACCOUNT'],
            "securityGroupName": self.TEST_SECURITY_GROUP,
            "cloudProvider": "azure",
            "type": "deleteSecurityGroup",
            "user": "[anonymous]"
        }],
        application=self.bindings['TEST_APP'],
        description='Delete Security Group: : ' + self.TEST_SECURITY_GROUP)


    builder = az.AzContractBuilder(self.az_observer)
    (builder.new_clause_builder(
        'Security Group Deleted', retryable_for_secs=30)
     .collect_resources(
         az_resource='network',
         command='nsg',
         args=['list', '--resource-group', self.TEST_SECURITY_GROUP_RG],
         no_resources_ok=True)
     .excludes_path_eq('name', self.TEST_SECURITY_GROUP)
    )
     
    return st.OperationContract(
        self.new_post_operation(
            title='delete_security_group', data=payload,
            path='applications/{app}/tasks'.format(app=self.bindings['TEST_APP'])),
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
      'TEST_APP': AzureSmokeTestScenario.DEFAULT_TEST_ID
      }

  return citest.base.TestRunner.main(
      parser_inits=[AzureSmokeTestScenario.initArgumentParser],
      default_binding_overrides=defaults,
      test_case_list=[AzureSmokeTest])

if __name__ == '__main__':
  sys.exit(main())

