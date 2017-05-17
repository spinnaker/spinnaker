'use strict';

const angular = require('angular');

import './instanceTypeSelector.directive.less';

module.exports = angular.module('spinnaker.core.serverGroup.configure.common.v2instanceTypeSelector', [])
  .directive('v2InstanceTypeSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./instanceTypeDirective.html'),
      controller: 'v2InstanceTypeSelectorCtrl as instanceTypeCtrl',
    };
  })
  .controller('v2InstanceTypeSelectorCtrl', function($scope, instanceTypeService) {
    function updateFamilies() {
      let command = $scope.command;
      let availableTypes = [];
      if (command.backingData && command.backingData.filtered) {
        availableTypes = command.backingData.filtered.instanceTypes || [];
      }
      instanceTypeService.getCategories(command.selectedProvider).then(function(categories) {
        categories.forEach(function(profile) {
          if (profile.type === command.viewState.instanceProfile) {
            if (!command.viewState.disableImageSelection) {
              profile.families.forEach((family) => {
                family.instanceTypes.forEach((instanceType) => {
                  instanceType.unavailable = availableTypes.every((available) => available !== instanceType.name);
                });
              });
            }
            $scope.selectedInstanceProfile = profile;
          }
        });
      });
    }

    $scope.$watch('command.viewState.instanceProfile', updateFamilies);
    $scope.$watch('command.backingData.filtered.instanceTypes', updateFamilies);

    this.selectInstanceType = function(type) {
      if (type.unavailable) {
        return;
      }
      $scope.command.instanceType = type.name;
      if ($scope.command.viewState.dirty && $scope.command.viewState.dirty.instanceType) {
        delete $scope.command.viewState.dirty.instanceType;
      }

      instanceTypeService.getInstanceTypeDetails($scope.command.selectedProvider, type.name).then(function(instanceTypeDetails) {
        $scope.command.viewState.instanceTypeDetails = instanceTypeDetails;
      });
    };

    this.getStorageDescription = function(instanceType) {
      if ($scope.command.instanceType === instanceType.name && $scope.command.viewState.overriddenStorageDescription) {
        return $scope.command.viewState.overriddenStorageDescription;
      } else {
        return instanceType.storage.count + 'x' + instanceType.storage.size;
      }
    };

    this.getStorageDescriptionHelpKey = function(instanceType) {
      return $scope.command.instanceType === instanceType.name && $scope.command.viewState.overriddenStorageDescription ? 'instanceType.storageOverridden' : null;
    };

  });
