'use strict';

angular.module('deckApp.delivery.manualPipelineExecution.controller', [
  //TODO(cfieber)-some things go here
  'deckApp.utils.lodash',
  'deckApp.pipelines.trigger.jenkins'
])
  .controller('ManualPipelineExecutionCtrl', function($scope, $modalInstance, pipelineRunner, igorService, pipeline, _) {
    $scope.triggers = _.chain(pipeline.triggers)
      .filter('type', 'jenkins')
      .sortBy('enabled')
      .map(function(trigger) {
        var copy = _.clone(trigger);
        copy.buildNumber = null;
        copy.type = 'manual';
        copy.description = copy.master + ': ' + copy.job;
        return copy;
      })
      .value();

    $scope.viewState = {
      triggering: false
    };

    $scope.trigger  = _.first($scope.triggers);
    $scope.builds = [];

    $scope.triggerUpdated = function() {
      console.log($scope.trigger);
      if (angular.isDefined($scope.trigger)) {
        igorService.listBuildsForJob($scope.trigger.master, $scope.trigger.job).then(function(builds) {
          $scope.builds = _.pluck(builds, 'number');

          if (!angular.isDefined($scope.trigger.build)) {
            $scope.trigger.buildNumber = _.first($scope.builds);
          }
        });
      } else {
        $scope.builds = [];
      }
    };

    (function() {
      $scope.triggerUpdated();
    })();

    this.cancel = function() {
      $modalInstance.close();
    };

    this.execute = function() {
      $scope.viewState.triggering = true;
      console.log($scope.trigger);

      pipelineRunner($scope.trigger).then(
        function() {
          $scope.viewState.triggering = false;
          $modalInstance.close();
        },
        function() {
          $scope.viewState.error = true;
          //This is bad and I feel bad:
          $modalInstance.close();
        }
      );
    };
  });
