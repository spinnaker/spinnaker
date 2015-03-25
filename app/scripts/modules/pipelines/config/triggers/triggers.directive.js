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
    };
  })
  .controller('triggersCtrl', function($scope, pipelineConfig) {
    this.addTrigger = function() {
      var triggerTypes = pipelineConfig.getTriggerTypes(),
          newTrigger = {enabled: true};
      if (!$scope.pipeline.triggers) {
        $scope.pipeline.triggers = [];
      }

      if (triggerTypes.length === 1) {
        newTrigger.type = triggerTypes[0].key;
      }
      $scope.pipeline.triggers.push(newTrigger);
    };


  });
