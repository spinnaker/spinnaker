'use strict';

require('../../app');
var angular = require('angular');

angular.module('deckApp')
  .controller('InstanceArchetypeCtrl', function($scope, instanceTypeService, modalWizardService, $) {

    instanceTypeService.getCategories().then(function(categories) {
      $scope.instanceProfiles = categories;
    });

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
