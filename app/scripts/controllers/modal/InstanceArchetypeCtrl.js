'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('InstanceArchetypeCtrl', function($scope, instanceTypeService, modalWizardService, $) {

    function populateAvailableTypesForRegion() {
      instanceTypeService.getAvailableTypesForRegions([$scope.command.region]).then(function (result) {
        $scope.regionalInstanceTypes = result;
      });
    }

    instanceTypeService.getCategories().then(function(categories) {
      $scope.instanceProfiles = categories;
    });

    if ($scope.command.region && $scope.command.instanceType) {
      $scope.command.instanceProfile = 'custom';
      populateAvailableTypesForRegion();
    }

    this.selectInstanceType = function (type, $event) {
      if ($event.target && $($event.target).is('select, a')) {
        return;
      }
      if ($scope.command.instanceProfile === type) {
        type = null;
      }
      $scope.command.instanceProfile = type;
      if ($scope.command.instanceProfile === 'custom') {
        modalWizardService.getWizard().excludePage('Instance Type');
        populateAvailableTypesForRegion();
      } else {
        modalWizardService.getWizard().includePage('Instance Type');
        $scope.instanceProfiles.forEach(function(profile) {
          if (profile.type === type) {
            $scope.selectedInstanceProfile = profile;
          }
        });
      }
    };

  });
