'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.serverGroup.configure.common.v2instanceArchetypeSelector', [
  require('./costFactor.js'),
  require('../../../presentation/isVisible/isVisible.directive.js'),
  require('../../../modal/wizard/modalWizard.service.js'),
  require('../../../modal/wizard/v2modalWizard.service.js'),
  require('../../../cloudProvider/cloudProvider.registry.js'),
  require('../../../utils/lodash.js'),
])
  .directive('v2InstanceArchetypeSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: require('./v2instanceArchetype.directive.html'),
      controller: 'v2InstanceArchetypeSelectorCtrl',
      controllerAs: 'instanceArchetypeCtrl'
    };
  })
  .controller('v2InstanceArchetypeSelectorCtrl', function($scope, instanceTypeService, infrastructureCaches,
                                                        serverGroupConfigurationService, modalWizardService,
                                                        v2modalWizardService, $log, cloudProviderRegistry, _) {
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

    this.selectInstanceType = function (type) {
      if ($scope.selectedInstanceProfile && $scope.selectedInstanceProfile.type === type) {
        type = null;
        $scope.selectedInstanceProfile = null;
      }
      $scope.command.viewState.instanceProfile = type;
      $scope.instanceProfiles.forEach(function(profile) {
        if (profile.type === type) {
          $scope.selectedInstanceProfile = profile;
          let current = $scope.command.instanceType;
          if (current && !_.includes(['custom', 'buildCustom'], profile.type)) {
            let found = profile.families
              .some((family) => family.instanceTypes.
                some((instanceType) => instanceType.name === current && !instanceType.unavailable)
            );
            if (!found) {
              $scope.command.instanceType = null;
            }
          }
        }
      });
    };

    this.updateInstanceType = () => {
      if ($scope.command.instanceType) {
        try {
          v2modalWizardService.markComplete('instance-type');
        } catch (e) {
          $log.warn('DEV NOTE: Using deprecated wizard service; consider upgrading to v2 modal wizard');
          modalWizardService.getWizard().markComplete('instance-type');
        }
      } else {
        try {
          v2modalWizardService.markIncomplete('instance-type');
        } catch (e) {
          $log.warn('DEV NOTE: Using deprecated wizard service; consider upgrading to v2 modal wizard');
          modalWizardService.getWizard().markIncomplete('instance-type');
        }
      }
    };

    $scope.$watch('command.instanceType', this.updateInstanceType);

    this.updateInstanceTypeDetails = () => {
      instanceTypeService.getInstanceTypeDetails($scope.command.selectedProvider, $scope.command.instanceType).then(function(instanceTypeDetails) {
        $scope.command.viewState.instanceTypeDetails = instanceTypeDetails;
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

    this.getInstanceBuilderTemplate = cloudProviderRegistry
      .getValue
      .bind(cloudProviderRegistry,
        $scope.command.cloudProvider,
        'instance.customInstanceBuilderTemplateUrl');

  });
