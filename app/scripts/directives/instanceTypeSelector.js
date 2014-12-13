'use strict';

angular.module('deckApp')
  .directive('instanceTypeSelector', function() {
    return {
      restrict: 'E',
      scope: {
        command: '=',
      },
      templateUrl: 'views/application/modal/serverGroup/aws/instanceTypeDirective.html',
      controller: 'InstanceTypeSelectorCtrl as instanceTypeCtrl',
    }
  })
  .controller('InstanceTypeSelectorCtrl', function($scope, instanceTypeService) {
    function updateFamilies() {
      instanceTypeService.getCategories($scope.command.selectedProvider).then(function(categories) {
        categories.forEach(function(profile) {
          if (profile.type === $scope.command.viewState.instanceProfile) {
            $scope.selectedInstanceProfile = profile;
          }
        });
      });
    }

    $scope.$watch('command.viewState.instanceProfile', updateFamilies);

    this.selectInstanceType = function(type) {
      $scope.command.instanceType = type;
    };

  });
