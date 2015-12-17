'use strict';

let angular = require('angular');

require('./instanceTypeSelector.directive.less');

module.exports = angular.module('spinnaker.core.serverGroup.configure.common.instanceTypeSelector', [])
  .directive('instanceTypeSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./instanceTypeDirective.html'),
      controller: 'InstanceTypeSelectorCtrl as instanceTypeCtrl',
    };
  })
  .controller('InstanceTypeSelectorCtrl', function($scope, instanceTypeService) {
    function updateFamilies() {
      let availableTypes = $scope.command.backingData.filtered.instanceTypes;
      instanceTypeService.getCategories($scope.command.selectedProvider).then(function(categories) {
        categories.forEach(function(profile) {
          if (profile.type === $scope.command.viewState.instanceProfile) {
            profile.families.forEach((family) => {
              family.instanceTypes.forEach((instanceType) => {
                instanceType.unavailable = availableTypes.every((available) => available !== instanceType.name);
              });
            });
            $scope.selectedInstanceProfile = profile;
          }
        });
      });
    }

    $scope.$watch('command.viewState.instanceProfile', updateFamilies);
    $scope.$watch('command.virtualizationType', updateFamilies);

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

  });
