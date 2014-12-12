'use strict';


angular.module('deckApp')
  .controller('awsServerGroupAdvancedSettingsCtrl', function($scope, modalWizardService) {

    modalWizardService.getWizard().markComplete('advanced');

    $scope.$watch('form.$valid', function(newVal) {
      if (newVal) {
        modalWizardService.getWizard().markClean('advanced');
      } else {
        modalWizardService.getWizard().markDirty('advanced');
      }
    });

  });
