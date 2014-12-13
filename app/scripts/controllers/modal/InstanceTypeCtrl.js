'use strict';


angular.module('deckApp')
  .controller('InstanceTypeCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('instance-type');
    modalWizardService.getWizard().markClean('instance-type');

  });
