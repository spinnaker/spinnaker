'use strict';

angular.module('deckApp.delivery')
  .factory('executionsService', function($stateParams, scheduler, orchestratedItem, $http, $timeout, settings, $q, RxService, applicationLevelScheduledCache, appendTransform) {

    function transformExecution(execution) {
      var stageSummaries = [],
          currentStageBuffer = [],
        currentStageSummary;
      execution.stages.forEach(function(stage, index) {
        stage.index = index;
        var owner = stage.syntheticStageOwner;
        if (owner === 'STAGE_BEFORE') {
          currentStageBuffer.push(stage);
        }
        if (owner === 'STAGE_AFTER') {
          currentStageSummary.stages.push(stage);
        }
        if (!owner) {
          var newSummary = {
            name: stage.name,
            stages: currentStageBuffer.concat([stage])
          };
          stageSummaries.push(newSummary);
          currentStageBuffer = [];
          currentStageSummary = newSummary;
        }
      });
      execution.stageSummaries = stageSummaries;
      execution.stageSummaries.forEach(function(summary) {
        if (summary.stages.length) {
          var lastStage = summary.stages[summary.stages.length - 1];
          summary.startTime = summary.stages[0].startTime;
          var currentStage = _(summary.stages).findLast(function(stage) { return !stage.hasNotStarted; }) || lastStage;
          summary.status = currentStage.status;
          summary.endTime = lastStage.endTime;
        }
      });
      stageSummaries.forEach(orchestratedItem.defineProperties);
    }

    function getExecutions() {
      var deferred = $q.defer();
      $http({
        method: 'GET',
        transformResponse: appendTransform(function(executions) {
          if (!executions || !executions.length) {
            return [];
          }
          executions.forEach(function(execution) {
            orchestratedItem.defineProperties(execution);
            execution.stages.forEach(function(stage) {
              orchestratedItem.defineProperties(stage);
              if (stage.tasks && stage.tasks.length) {
                stage.tasks.forEach(orchestratedItem.defineProperties);
              }
            });
            transformExecution(execution);
          });
          return executions;
        }),
        url: [
          settings.gateUrl,
          'applications',
          $stateParams.application,
          'pipelines',
        ].join('/'),
      }).then(
        function(resp) {
          deferred.resolve(resp.data);
        },
        function(resp) {
          deferred.reject(resp);
        }
      );
      return deferred.promise;
    }

    function waitUntilNewTriggeredPipelineAppears(pipelineName, ignoreList) {

      return getExecutions().then(function(executions) {
        var match = executions.filter(function(execution) {
          return (execution.status === 'RUNNING' || execution.status === 'NOT_STARTED') &&
            execution.name === pipelineName &&
            ignoreList.indexOf(execution.id) === -1;
        });
        var deferred = $q.defer();
        if (match && match.length) {
          deferred.resolve();
          return deferred.promise;
        } else {
          return $timeout(function() {
            return waitUntilNewTriggeredPipelineAppears(pipelineName, ignoreList);
          }, 1000);
        }
      });
    }

    return {
      getAll: getExecutions,
      forceRefresh: scheduler.scheduleImmediate,
      subscribeAll: function(fn) {
        return scheduler
          .get()
          .flatMap(function() {
            return RxService.Observable.fromPromise(getExecutions());
          })
          .subscribe(fn);
      },
      waitUntilNewTriggeredPipelineAppears: waitUntilNewTriggeredPipelineAppears,
    };
  });
