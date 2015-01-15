'use strict';


angular.module('deckApp.serverGroup.configure.aws')
  .controller('awsInstanceTypeCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('instance-type');
    modalWizardService.getWizard().markClean('instance-type');

  });
