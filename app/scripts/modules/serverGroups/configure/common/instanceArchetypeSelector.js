'use strict';

angular.module('deckApp.serverGroup.configure.common')
  .directive('instanceArchetypeSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'scripts/modules/serverGroups/configure/common/instanceArchetypeDirective.html',
      controller: 'InstanceArchetypeSelectorCtrl as instanceArchetypeCtrl',
    };
  })
  .controller('InstanceArchetypeSelectorCtrl', function($scope, instanceTypeService, _) {
    var controller = this;
    instanceTypeService.getCategories($scope.command.selectedProvider).then(function(categories) {
      $scope.instanceProfiles = categories;
      $scope.command.viewState.instanceProfile = controller.determineInstanceProfileFromType($scope.command.instanceType);
      controller.selectInstanceType($scope.command.viewState.instanceProfile);
    });

    this.determineInstanceProfileFromType = function(instanceType) {
      var profilePrefix = instanceType ? _.first(instanceType.split('.')) : null;

      switch(profilePrefix) {
        case 't2':
          return 'micro';
        case 'r3':
          return 'memory';
        case 'm3':
          return 'general';
        default:
          return 'custom';
      }

    };

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

  });
