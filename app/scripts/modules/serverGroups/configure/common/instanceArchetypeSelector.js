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
      $scope.command.viewState.instanceProfile = controller.findProfileForInstanceType(categories, $scope.command.instanceType);
      controller.selectInstanceType($scope.command.viewState.instanceProfile);
    });

    this.findProfileForInstanceType = function(categories, instanceType) {
      var query = {families: [ {instanceTypes: [ {name:instanceType } ] } ] } ;
      var result = _.result(_.findWhere(categories, query), 'type');
      return result || 'custom';
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
