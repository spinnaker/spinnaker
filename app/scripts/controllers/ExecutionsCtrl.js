'use strict';

angular.module('deckApp')
  .controller('ExecutionsCtrl', function($scope, pipelines) {
    $scope.filterFn = function(item) {
      var stateFilter = function(item) {
        switch ($scope.filter.pipelineState.toLowerCase()) {
          case 'all':
            return item;
          case 'running':
            return item.isRunning;
          case 'completed':
            return item.isCompleted;
          case 'failed':
            return item.isFailed;
        }
      };

      var typeFilter = function(item) {
        switch ($scope.filter.triggerType.toLowerCase()) {
          case 'all':
            return item;
          case 'jenkins':
            return item.trigger.type === 'jenkins';
          default:
            return item;
        }
      };

      return [stateFilter, typeFilter].every(function(fn) {
        return fn(item);
      });
    };

    pipelines.getAll().then(function(p) {
      $scope.pipelines = p;
      var subscription = pipelines.subscribeAll(function(p) {
        $scope.pipelines = p;
      });
      $scope.$on('$destroy', function() {
        subscription.dispose();
      });
    });

    $scope.sortOptions = [
      {
        label: 'Start time',
        value: 'startTime',
      },
      /*{
        label: 'Execution #',
        value: 'id',
      },*/
      {
        label: 'End time',
        value: 'endTime',
      },
    ];

    $scope.filter = {
      sortKey: 'startTime',
      pipelineState: 'All',
      triggerType: 'All',
      groupByPipeline: true,
    };
  });
