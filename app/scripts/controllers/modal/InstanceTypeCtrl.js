'use strict';


angular.module('deckApp')
  .controller('InstanceTypeCtrl', function($scope, instanceTypeService, modalWizardService) {

    modalWizardService.getWizard().markComplete('instance-type');
    modalWizardService.getWizard().markClean('instance-type');

    $scope.instanceTypeCtrl = this;

    instanceTypeService.getCategories().then(function(categories) {
      categories.forEach(function(profile) {
        if (profile.type === $scope.command.instanceProfile) {
          $scope.selectedInstanceProfile = profile;
        }
      });
    });

    this.selectInstanceType = function(type) {
      $scope.command.instanceType = type;
    };

  });
