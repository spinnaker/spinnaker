'use strict';

angular.module('deckApp')
  .directive('instanceArchetypeSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'views/application/modal/serverGroup/aws/instanceArchetypeDirective.html',
      controller: 'InstanceArchetypeSelectorCtrl as instanceArchetypeCtrl',
    };
  })
  .controller('InstanceArchetypeSelectorCtrl', function($scope, instanceTypeService) {
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

  });
