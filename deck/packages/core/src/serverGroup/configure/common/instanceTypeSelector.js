'use strict';

import { module } from 'angular';

import './instanceTypeSelector.directive.less';

export const CORE_SERVERGROUP_CONFIGURE_COMMON_INSTANCETYPESELECTOR =
  'spinnaker.core.serverGroup.configure.common.instanceTypeSelector';
export const name = CORE_SERVERGROUP_CONFIGURE_COMMON_INSTANCETYPESELECTOR; // for backwards compatibility
module(CORE_SERVERGROUP_CONFIGURE_COMMON_INSTANCETYPESELECTOR, [])
  .directive('instanceTypeSelector', function () {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./instanceTypeDirective.html'),
      controller: 'InstanceTypeSelectorCtrl as instanceTypeCtrl',
    };
  })
  .controller('InstanceTypeSelectorCtrl', [
    '$scope',
    'instanceTypeService',
    function ($scope, instanceTypeService) {
      function updateFamilies() {
        const availableTypes = $scope.command.backingData.filtered.instanceTypes;
        instanceTypeService.getCategories($scope.command.selectedProvider).then(function (categories) {
          categories.forEach(function (profile) {
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

      this.selectInstanceType = function (type) {
        if (type.unavailable) {
          return;
        }
        $scope.command.instanceType = type.name;
        if ($scope.command.viewState.dirty && $scope.command.viewState.dirty.instanceType) {
          delete $scope.command.viewState.dirty.instanceType;
        }

        instanceTypeService
          .getInstanceTypeDetails($scope.command.selectedProvider, type.name)
          .then(function (instanceTypeDetails) {
            $scope.command.viewState.instanceTypeDetails = instanceTypeDetails;
          });
      };

      this.getStorageDescription = function (instanceType) {
        if (
          $scope.command.instanceType === instanceType.name &&
          $scope.command.viewState.overriddenStorageDescription
        ) {
          return $scope.command.viewState.overriddenStorageDescription;
        } else {
          return instanceType.storage.count + 'x' + instanceType.storage.size;
        }
      };

      this.getStorageDescriptionHelpKey = function (instanceType) {
        return $scope.command.instanceType === instanceType.name &&
          $scope.command.viewState.overriddenStorageDescription
          ? 'instanceType.storageOverridden'
          : null;
      };
    },
  ]);
