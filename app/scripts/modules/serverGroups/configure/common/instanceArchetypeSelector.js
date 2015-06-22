'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.common.instanceArchetypeSelector', [])
  .directive('instanceArchetypeSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./instanceArchetypeDirective.html'),
      controller: 'InstanceArchetypeSelectorCtrl',
      controllerAs: 'instanceArchetypeCtrl'
    };
  })
  .controller('InstanceArchetypeSelectorCtrl', function($scope, instanceTypeService, infrastructureCaches, serverGroupConfigurationService) {
    var controller = this;
    instanceTypeService.getCategories($scope.command.selectedProvider).then(function(categories) {
      $scope.instanceProfiles = categories;
      controller.selectInstanceType($scope.command.viewState.instanceProfile);
    });

    this.selectInstanceType = function (type) {
      if ($scope.selectedInstanceProfile && $scope.selectedInstanceProfile.type === type) {
        type = null;
      }
      $scope.command.viewState.instanceProfile = type;
      $scope.instanceProfiles.forEach(function(profile) {
        if (profile.type === type) {
          $scope.selectedInstanceProfile = profile;
        }
      });
    };

    if ($scope.command.region && $scope.command.instanceType && !$scope.command.viewState.instanceProfile) {
      this.selectInstanceType('custom');
    }

    this.getInstanceTypeRefreshTime = function() {
      return infrastructureCaches.instanceTypes.getStats().ageMax;
    };

    this.refreshInstanceTypes = function() {
      controller.refreshing = true;
      serverGroupConfigurationService.refreshInstanceTypes($scope.command.selectedProvider, $scope.command).then(function() {
        controller.refreshing = false;
      });
    };

    // if there are no instance types in the cache, try to reload them
    instanceTypeService.getAllTypesByRegion($scope.command.selectedProvider).then(function(results) {
      if (!results || !Object.keys(results).length) {
        controller.refreshInstanceTypes();
      }
    });

  }).name;
