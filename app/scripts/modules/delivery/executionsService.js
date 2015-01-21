'use strict';

angular.module('deckApp.delivery')
  .factory('executionsService', function($stateParams, scheduler, orchestratedItem, $http, $timeout, settings, $q, RxService, applicationLevelScheduledCache, appendTransform) {

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

            Object.defineProperty(execution, 'currentStage', {
              get: function() {
                if (execution.isCompleted) {
                  return execution.stages.indexOf(
                    execution.stages[execution.stages.length -1]
                  );
                }
                if (execution.isFailed) {
                  return execution.stages.indexOf(
                    execution.stages.filter(function(stage) {
                      return stage.isFailed;
                    })[0]
                  );
                }
                if (execution.isRunning) {
                  return execution.stages.indexOf(
                    execution.stages.filter(function(stage) {
                      return stage.isRunning;
                    })[0]
                  );
                }
              },
            });
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
