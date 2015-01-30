'use strict';

angular.module('deckApp.delivery')
  .factory('executionsService', function($stateParams, scheduler, orchestratedItem, $http, $timeout, settings, $q, RxService, appendTransform, executionsTransformer) {

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
            executionsTransformer.transformExecution(execution);
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

    function cancelExecution(executionId) {
      var deferred = $q.defer();
      $http({
        method: 'PUT',
        url: [
          settings.gateUrl,
          'applications',
          $stateParams.application,
          'pipelines',
          executionId,
          'cancel',
        ].join('/')
      }).then(
          function() {
            scheduler.scheduleImmediate();
            deferred.resolve();
          },
          function(exception) {
            deferred.reject(exception.message);
          }
        );
      return deferred.promise;
    }

    return {
      getAll: getExecutions,
      cancelExecution: cancelExecution,
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
