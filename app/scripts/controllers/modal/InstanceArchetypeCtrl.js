'use strict';


angular.module('deckApp')
  .controller('InstanceArchetypeCtrl', function($scope, instanceTypeService, modalWizardService) {

    var wizard = modalWizardService.getWizard();

    instanceTypeService.getCategories($scope.command.selectedProvider).then(function(categories) {
      $scope.instanceProfiles = categories;
    });

    if ($scope.command.region && $scope.command.instanceType && !$scope.command.viewState.instanceProfile) {
      $scope.command.viewState.instanceProfile = 'custom';
    }

    this.selectInstanceType = function (type) {
      if ($scope.command.viewState.instanceProfile === type) {
        type = null;
      }
      $scope.command.viewState.instanceProfile = type;
      if ($scope.command.viewState.instanceProfile === 'custom') {
        wizard.excludePage('instance-type');
      } else {
        wizard.includePage('instance-type');
        wizard.markClean('instance-profile');
        wizard.markComplete('instance-profile');
        $scope.instanceProfiles.forEach(function(profile) {
          if (profile.type === type) {
            $scope.selectedInstanceProfile = profile;
          }
        });
      }
    };

    this.instanceTypeSelected = function() {
      if ($scope.command.instanceType) {
        wizard.markClean('instance-profile');
        wizard.markComplete('instance-profile');
      }
    };

  });
