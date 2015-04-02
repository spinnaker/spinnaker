'use strict';

angular.module('deckApp.delivery.executions.service', [
  'ui.router',
  'deckApp.scheduler',
  'deckApp.orchestratedItem.service',
  'deckApp.settings',
  'deckApp.utils.rx',
  'deckApp.utils.appendTransform',
  'deckApp.delivery.executionTransformer.service'
])
  .factory('executionsService', function($stateParams, $http, $timeout, $q, scheduler, orchestratedItem, settings, RxService, appendTransform, executionsTransformer) {

    function getExecutions(applicationName) {
      var deferred = $q.defer();
      $http({
        method: 'GET',
        transformResponse: appendTransform(function(executions) {
          if (!executions || !executions.length) {
            return [];
          }
          executions.forEach(executionsTransformer.transformExecution);
          return executions;
        }),
        url: [
          settings.gateUrl,
          'applications',
          applicationName,
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

    function waitUntilNewTriggeredPipelineAppears(application, pipelineName, ignoreList) {

      return application.reloadExecutions().then(function() {
        var executions = application.executions;
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
            return waitUntilNewTriggeredPipelineAppears(application, pipelineName, ignoreList);
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
            deferred.reject(exception && exception.data ? exception.message : null);
          }
        );
      return deferred.promise;
    }

    function deleteExecution(application, executionId) {
      var deferred = $q.defer();
      $http({
        method: 'DELETE',
        url: [
          settings.gateUrl,
          'pipelines',
          executionId,
        ].join('/')
      }).then(
        function() {
          application.reloadExecutions().then(deferred.resolve);
        },
        function(exception) {
          deferred.reject(exception && exception.data ? exception.data.message : null);
        }
      );
      return deferred.promise;
    }

    function getSectionCacheKey(groupBy, application, heading) {
      return ['pipeline', groupBy, application, heading].join('#');
    }

    return {
      getAll: getExecutions,
      cancelExecution: cancelExecution,
      deleteExecution: deleteExecution,
      forceRefresh: scheduler.scheduleImmediate,
      subscribeAll: function(fn) {
        return scheduler
          .get()
          .flatMap(function() {
            return RxService.Observable.fromPromise(getExecutions($stateParams.application));
          })
          .subscribe(fn);
      },
      waitUntilNewTriggeredPipelineAppears: waitUntilNewTriggeredPipelineAppears,
      getSectionCacheKey: getSectionCacheKey,
    };
  });
