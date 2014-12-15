'use strict';

angular.module('deckApp.delivery')
  .controller('pipelineExecutions', function($scope, executionsService, d3Service) {
    var controller = this;

    $scope.filter = {
      execution: {
        status: {
          running: true,
          completed: true,
          failed: true,
        },
        triggers: {
          jenkins: true,
        },
        groupBy: 'name',
        sortBy: 'startTime',
      },
      stage: {
        max: 0,
        name: {},
        solo: {
          facet: false,
          value: false,
        },
        status: {
          running: true,
          completed: true,
          failed: true,
        },
        scale: 'fixed',
        colorOverlay: 'status',
      },
    };

    $scope.statusDisplayNames = {
      'failed': 'Failed',
      'terminal': 'Failed',
      //'not_started': 'Not Started',
      'running': 'Running',
      'completed': 'Completed',
    };

    $scope.scale = {
      name: d3Service
        .scale
        .category10(),
      status: d3Service
        .scale
        .ordinal()
        .domain(['succeeded', 'failed', 'terminal', 'running', 'not_started'])
        .range(['#c0d89d', '#b82525', '#b82525', '#2275b8', '#ffffff']),
    };

    controller.solo = function(facet, value) {
      $scope.filter.stage.solo.facet = facet;
      $scope.filter.stage.solo.value = value;
    };

    controller.endSolo = function() {
      $scope.filter.stage.solo.facet = false;
      $scope.filter.stage.solo.value = false;
    };

    controller.updateLegend = function() {
      var stageNames = Object.keys($scope.executions.reduce(function(acc, cur) {
        cur.stages.forEach(function(stage) {
          acc[stage.name] = true;
        });
        return acc;
      }, {}));

      $scope.scale.name(stageNames);

      stageNames.forEach(function(name) {
        if ($scope.filter.stage.name[name] === undefined) {
          $scope.filter.stage.name[name] = true;
        }
      });
    };

    executionsService.getAll().then(function(e) {
      $scope.filter.stage.max = e.reduce(function(acc, execution) {
        return execution.stages.length > acc ? execution.stages.length : acc;
      }, 0);
      $scope.executions = e;
      controller.updateLegend();
      var subscription = executionsService.subscribeAll(function(e) {
        $scope.filter.stage.max = e.reduce(function(acc, execution) {
          return execution.stages.length > acc ? execution.stages.length : acc;
        }, 0);
        controller.updateLegend();
        $scope.executions = e;
      });
      $scope.$on('$destroy', function() {
        subscription.dispose();
      });
    });
  });
