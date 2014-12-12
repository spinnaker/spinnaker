'use strict';

angular.module('deckApp.pipelines.trigger')
  .directive('triggers', function() {
    return {
      restrict: 'E',
      scope: {
        pipeline: '='
      },
      controller: 'triggersCtrl',
      controllerAs: 'triggersCtrl',
      templateUrl: 'scripts/modules/pipelines/config/triggers/triggers.html'
    }
  })
  .controller('triggersCtrl', function($scope) {
    this.addTrigger = function() {
      if (!$scope.pipeline.triggers) {
        $scope.pipeline.triggers = [];
      }
      $scope.pipeline.triggers.push({enabled: true});
    };


  });
