'use strict';

angular.module('deckApp.delivery.pipelineExecutions.controller', [
  'deckApp.delivery.executions.service',
  'deckApp.utils.d3',
  'deckApp.pipelines.config.service',
  'deckApp.utils.scrollTo',
  'deckApp.caches.collapsibleSectionState',
  'deckApp.caches.viewStateCache',
])
  .controller('pipelineExecutions', function($scope, $state, d3Service,
                                             pipelineConfigService, scrollToService, executionsService,
                                             viewStateCache, collapsibleSectionStateCache) {

    var controller = this;

    var executionsViewStateCache = viewStateCache.executions || viewStateCache.createCache('executions', { version: 1 });

    function cacheViewState() {
      executionsViewStateCache.put($scope.application.name, $scope.filter);
    }

    $scope.viewState = {
      loading: true
    };

    $scope.filter = executionsViewStateCache.get($scope.application.name) || {
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
        .domain(['completed', 'failed', 'running', 'not_started', 'canceled', 'suspended', 'unknown'])
        .range(['#769D3E', '#b82525','#2275b8', '#cccccc', '#cccccc', '#cccccc', '#cccccc']),
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

    function normalizeExecutionNames(executions) {
      var configurations = $scope.configurations || [];
      executions.forEach(function(execution) {
        if (execution.pipelineConfigId) {
          var configMatches = configurations.filter(function(configuration) {
            return configuration.id === execution.pipelineConfigId;
          });
          if (configMatches.length) {
            execution.name = configMatches[0].name;
          }
        }
      });
    }

    function updateExecutions() {
      var executions = $scope.application.executions || [];
      normalizeExecutionNames(executions);
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

    function dataInitializationSuccess() {
      updateExecutions();
      $scope.viewState.loading = false;
      // if we detected the loading of a details section, scroll it into view
      if ($scope.detailsTarget) {
        // make sure it's expanded
        var pipelines = $scope.executions.filter(function(execution) {
          return execution.id === $scope.detailsTarget;
        });
        if (pipelines.length) {
          collapsibleSectionStateCache.setExpanded(
            executionsService.getSectionCacheKey($scope.filter.execution.groupBy, $scope.application.name, pipelines[0].name),
            true);
          scrollToService.scrollTo('execution-' + $scope.detailsTarget, '.execution-groups', 300);
        }
      }
      var noExecutions = !$scope.executions || !$scope.executions.length;
      var noConfigurations = !$scope.configurations.length;
      if(noExecutions && noConfigurations) {
        $state.go('^.pipelineConfig');
      }
    }

    function dataInitializationFailure() {
      $scope.viewState.loading = false;
      $scope.viewState.initializationError = true;
    }

    pipelineConfigService.getPipelinesForApplication($scope.application.name).then(function(configurations) {
        $scope.configurations = configurations;
        if ($scope.application.executionsLoaded) {
          dataInitializationSuccess();
        } else {
          $scope.$on('executions-loaded', dataInitializationSuccess);
        }
      },
      dataInitializationFailure);

    $scope.$on('executions-reloaded', updateExecutions);
    $scope.$watch('filter', cacheViewState, true);

  });
