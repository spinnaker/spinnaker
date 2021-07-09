'use strict';

import { module } from 'angular';

export const AZURE_SERVERGROUP_CONFIGURE_WIZARD_SERVERGROUPINSTANCETYPE_CONTROLLER =
  'spinnaker.azure.serverGroup.configure.instanceType.controller';
export const name = AZURE_SERVERGROUP_CONFIGURE_WIZARD_SERVERGROUPINSTANCETYPE_CONTROLLER; // for backwards compatibility
module(AZURE_SERVERGROUP_CONFIGURE_WIZARD_SERVERGROUPINSTANCETYPE_CONTROLLER, []).controller('azureInstanceTypeCtrl', [
  '$scope',
  'modalWizardService',
  function ($scope, modalWizardService) {
    modalWizardService.getWizard().markComplete('instance-type');
    modalWizardService.getWizard().markClean('instance-type');
  },
]);
