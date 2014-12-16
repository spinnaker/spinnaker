'use strict';


angular.module('deckApp')
  .controller('gceInstanceTypeCtrl', function($scope, instanceTypeService, modalWizardService) {

    modalWizardService.getWizard().markComplete('instance-type');
    modalWizardService.getWizard().markClean('instance-type');

    $scope.instanceTypeCtrl = this;

    instanceTypeService.getCategories($scope.command.selectedProvider).then(function(categories) {
      categories.forEach(function(profile) {
        if (profile.type === $scope.command.viewState.instanceProfile) {
          $scope.selectedInstanceProfile = profile;
        }
      });
    });

    this.selectInstanceType = function(type) {
      $scope.command.instanceType = type;
    };

  });
