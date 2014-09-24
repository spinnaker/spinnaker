'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('InstanceArchetypeCtrl', function($scope, instanceTypeService, modalWizardService) {

    var wizard = modalWizardService.getWizard();

    instanceTypeService.getCategories().then(function(categories) {
      $scope.instanceProfiles = categories;
    });

    if ($scope.command.region && $scope.command.instanceType && !$scope.command.instanceProfile) {
      $scope.command.instanceProfile = 'custom';
    }

    this.selectInstanceType = function (type) {
      if ($scope.command.instanceProfile === type) {
        type = null;
      }
      $scope.command.instanceProfile = type;
      if ($scope.command.instanceProfile === 'custom') {
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
