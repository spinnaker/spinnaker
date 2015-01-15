'use strict';


angular.module('deckApp.serverGroup.configure.aws')
  .controller('awsServerGroupSecurityGroupsCtrl', function(modalWizardService) {
    modalWizardService.getWizard().markClean('security-groups');
    modalWizardService.getWizard().markComplete('security-groups');
  });
