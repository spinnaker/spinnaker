'use strict';

const angular = require('angular');

export const CORE_SERVERGROUP_CONFIGURE_COMMON_INSTANCEARCHETYPESELECTOR =
  'spinnaker.core.serverGroup.configure.common.instanceArchetypeSelector';
export const name = CORE_SERVERGROUP_CONFIGURE_COMMON_INSTANCEARCHETYPESELECTOR; // for backwards compatibility
angular
  .module(CORE_SERVERGROUP_CONFIGURE_COMMON_INSTANCEARCHETYPESELECTOR, [
    require('./costFactor').name,
    require('../../../presentation/isVisible/isVisible.directive').name,
    require('./dirtyInstanceTypeNotification.component').name,
  ])
  .directive('instanceArchetypeSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./instanceArchetypeDirective.html'),
      controller: 'InstanceArchetypeSelectorCtrl',
      controllerAs: 'instanceArchetypeCtrl',
    };
  })
  .controller('InstanceArchetypeSelectorCtrl', [
    '$scope',
    'instanceTypeService',
    function($scope, instanceTypeService) {
      var controller = this;
      instanceTypeService.getCategories($scope.command.selectedProvider).then(function(categories) {
        $scope.instanceProfiles = categories;
        if ($scope.instanceProfiles.length % 3 === 0) {
          $scope.columns = 3;
        }
        if ($scope.instanceProfiles.length % 4 === 0) {
          $scope.columns = 4;
        }
        if ($scope.instanceProfiles.length % 5 === 0 || $scope.instanceProfiles.length === 7) {
          $scope.columns = 5;
        }
        controller.selectInstanceType($scope.command.viewState.instanceProfile);
      });

      this.selectInstanceType = function(type) {
        if ($scope.selectedInstanceProfile && $scope.selectedInstanceProfile.type === type) {
          type = null;
          $scope.selectedInstanceProfile = null;
        }
        $scope.command.viewState.instanceProfile = type;
        $scope.instanceProfiles.forEach(function(profile) {
          if (profile.type === type) {
            $scope.selectedInstanceProfile = profile;
          }
        });
      };

      this.updateInstanceTypeDetails = function() {
        instanceTypeService
          .getInstanceTypeDetails($scope.command.selectedProvider, $scope.command.instanceType)
          .then(function(instanceTypeDetails) {
            $scope.command.viewState.instanceTypeDetails = instanceTypeDetails;
          });
      };

      if ($scope.command.region && $scope.command.instanceType && !$scope.command.viewState.instanceProfile) {
        this.selectInstanceType('custom');
      }
    },
  ]);
