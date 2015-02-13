'use strict';

angular.module('deckApp.delivery.pipelineExecutions.controller', [
  'deckApp.delivery.executions.service',
  'deckApp.utils.d3',
  'deckApp.pipelines.config.service',
  'deckApp.utils.scrollTo'
])
  .controller('pipelineExecutions', function($scope, $q, $state, executionsService, d3Service, pipelineConfigService, scrollToService) {
    var controller = this;

    $scope.viewState = {
      loading: true
    };

    $scope.filter = {
      count: 5,
      execution: {
        status: {
          running: true,
          completed: true,
          failed: true,
          'not_started': true,
          canceled: false,
          suspended: true,
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
          'not_started': true,
          suspended: true,
        },
        scale: 'fixed',
        colorOverlay: 'status',
      },
    };

    $scope.statusDisplayNames = {
      failed: 'Failed',
      'not_started': 'Not Started',
      running: 'Running',
      completed: 'Completed',
      canceled: 'Canceled',
      suspended: 'Suspended',
    };

    $scope.scale = {
      name: d3Service
        .scale
        .category10(),
      status: d3Service
        .scale
        .ordinal()
        .domain(['completed', 'failed', 'running', 'not_started', 'canceled', 'suspended'])
        .range(['#769D3E', '#b82525','#2275b8', '#cccccc', '#cccccc', '#cccccc']),
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
        cur.stageSummaries.forEach(function(stage) {
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

    function updateExecutions(executions) {
      $scope.filter.stage.max = executions.reduce(function(acc, execution) {
        return execution.stageSummaries.length > acc ? execution.stageSummaries.length : acc;
      }, 0);
      $scope.executions = executions;
      controller.updateLegend();
    }

    // The executionId will not be available in the $stateParams that would be passed into this controller
    // because that field belongs to a child state. So we have to watch for a $stateChangeSuccess event, then set
    // the value on the scope
    $scope.$on('$stateChangeSuccess', function(event, toState, toParams) {
      $scope.detailsTarget = toParams.executionId;
    });

    function dataInitializationSuccess(results) {
      $scope.viewState.loading = false;
      $scope.configurations = results.configurations.plain();
      updateExecutions(results.executions);
      var subscription = executionsService.subscribeAll(updateExecutions);
      $scope.$on('$destroy', function() {
        subscription.dispose();
      });
      // if we detected the loading of a details section, scroll it into view
      if ($scope.detailsTarget) {
        scrollToService.scrollTo('execution-' + $scope.detailsTarget, '.execution-groups', 415);
      }
      var noExecutions = !results.executions || !results.executions.length;
      var noConfigurations = !results.configurations.length;
      if(noExecutions && noConfigurations) {
        $state.go('^.pipelineConfig');
      }
    }

    function dataInitializationFailure() {
      $scope.viewState.loading = false;
      $scope.viewState.initializationError = true;
    }

    $q.all({
      configurations: pipelineConfigService.getPipelinesForApplication($scope.application.name),
      executions: executionsService.getAll()
    }).then(dataInitializationSuccess, dataInitializationFailure);
  });
