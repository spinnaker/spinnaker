'use strict';
let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executions.service', [
  require('../../cache/deckCacheFactory.js'),
  require('../../utils/appendTransform.js'),
  require('../../config/settings.js'),
  require('./executions.transformer.service.js')
])
  .factory('executionService', function($http, $timeout, $q, $log,
                                         settings, appendTransform, executionsTransformer) {

    const activeStatuses = ['RUNNING', 'SUSPENDED', 'PAUSED', 'NOT_STARTED'];

    function getRunningExecutions(applicationName) {
      return getExecutions(applicationName, activeStatuses);
    }

    function getExecutions(applicationName, statuses = []) {
      let url = [ settings.gateUrl, 'applications', applicationName, 'pipelines'].join('/');
      if (statuses.length) {
        url += '?statuses=' + statuses.map((status) => status.toUpperCase()).join(',');
      }
      return $http({
        method: 'GET',
        url: url,
        timeout: settings.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      })
        .then((resp) => resp.data);
    }

    function getExecution(executionId) {
      const url = [ settings.gateUrl, 'pipelines', executionId].join('/');
      return $http({
        method: 'GET',
        url: url,
        timeout: settings.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      }).then((resp) => resp.data);
    }

    function transformExecutions(application, executions) {
      if (!executions || !executions.length) {
        return;
      }
      executions.forEach((execution) => {
        let stringVal = JSON.stringify(execution);
        // do not transform if it hasn't changed
        let match = (application.executions || []).filter((test) => test.id === execution.id);
        if (!match.length || !match[0].stringVal || match[0].stringVal !== stringVal) {
          execution.stringVal = stringVal;
          executionsTransformer.transformExecution(application, execution);
        }
      });
    }

    function waitUntilNewTriggeredPipelineAppears(application, pipelineName, triggeredPipelineId) {

      return getRunningExecutions(application.name).then(function(executions) {
        var match = executions.filter(function(execution) {
          return execution.id === triggeredPipelineId;
        });
        var deferred = $q.defer();
        if (match && match.length) {
          application.reloadExecutions().then(deferred.resolve);
          return deferred.promise;
        } else {
          return $timeout(function() {
            return waitUntilNewTriggeredPipelineAppears(application, pipelineName, triggeredPipelineId);
          }, 1000);
        }
      });
    }

    function waitUntilPipelineIsCancelled(application, executionId) {
      return getRunningExecutions(application.name).then((executions) => {
        let match = executions.filter((execution) => execution.id === executionId);
        let deferred = $q.defer();
        if (match && !match.length) {
          application.reloadExecutions().then(deferred.resolve);
          return deferred.promise;
        }
        return $timeout(() => waitUntilPipelineIsCancelled(application, executionId), 1000);
      });
    }

    function waitUntilPipelineIsDeleted(application, executionId) {

      return getExecutions(application.name).then((executions) => {
        let match = executions.filter((execution) => execution.id === executionId);
        let deferred = $q.defer();
        if (match && !match.length) {
          application.reloadExecutions().then(deferred.resolve);
          return deferred.promise;
        }
        return $timeout(() => waitUntilPipelineIsDeleted(application, executionId), 1000);
      });
    }

    function cancelExecution(application, executionId) {
      var deferred = $q.defer();
      $http({
        method: 'PUT',
        url: [
          settings.gateUrl,
          'applications',
          application.name,
          'pipelines',
          executionId,
          'cancel',
        ].join('/')
      }).then(
        () => waitUntilPipelineIsCancelled(application, executionId).then(deferred.resolve),
        (exception) => deferred.reject(exception && exception.data ? exception.message : null)
      );
      return deferred.promise;
    }

    function pauseExecution(application, executionId) {
      var deferred = $q.defer();
      var matcher = (execution) => {
        return execution.status === 'PAUSED';
      };

      $http({
        method: 'PUT',
        url: [
          settings.gateUrl,
          'pipelines',
          executionId,
          'pause',
        ].join('/')
      }).then(
        () => waitUntilExecutionMatches(executionId, matcher).then(application.reloadExecutions).then(deferred.resolve),
        (exception) => deferred.reject(exception && exception.data ? exception.message : null)
    );
      return deferred.promise;
    }

    function resumeExecution(application, executionId) {
      var deferred = $q.defer();
      var matcher = (execution) => {
        return execution.status === 'RUNNING';
      };

      $http({
        method: 'PUT',
        url: [
          settings.gateUrl,
          'pipelines',
          executionId,
          'resume',
        ].join('/')
      }).then(
        () => waitUntilExecutionMatches(executionId, matcher).then(application.reloadExecutions).then(deferred.resolve),
        (exception) => deferred.reject(exception && exception.data ? exception.message : null)
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
        () => waitUntilPipelineIsDeleted(application, executionId).then(deferred.resolve),
        (exception) => deferred.reject(exception && exception.data ? exception.data.message : null)
      );
      return deferred.promise;
    }

    function waitUntilExecutionMatches(executionId, closure) {
      return getExecution(executionId).then(
        (execution) => {
          if (closure(execution)) {
            return execution;
          }
          return $timeout(() => waitUntilExecutionMatches(executionId, closure), 1000);
        }
      );
    }

    function getSectionCacheKey(groupBy, application, heading) {
      return ['pipeline', groupBy, application, heading].join('#');
    }

    function getProjectExecutions(project, limit = 1) {
      return $http({
        method: 'GET',
        transformResponse: appendTransform(function(executions) {
          if (!executions || !executions.length) {
            return [];
          }
          executions.forEach(function(execution) {
            executionsTransformer.transformExecution({}, execution);
          });
          return executions;
        }),
        url: [
          settings.gateUrl,
          'projects',
          project,
          'pipelines'
        ].join('/') + '?limit=' + limit
      }).then((resp) => {
        return resp.data.sort((a, b) => b.startTime - (a.startTime || new Date().getTime()));
      });
    }

    return {
      getExecutions: getExecutions,
      getRunningExecutions: getRunningExecutions,
      transformExecutions: transformExecutions,
      cancelExecution: cancelExecution,
      resumeExecution: resumeExecution,
      pauseExecution: pauseExecution,
      deleteExecution: deleteExecution,
      waitUntilNewTriggeredPipelineAppears: waitUntilNewTriggeredPipelineAppears,
      waitUntilExecutionMatches: waitUntilExecutionMatches,
      getSectionCacheKey: getSectionCacheKey,
      getProjectExecutions: getProjectExecutions,
    };
  });
