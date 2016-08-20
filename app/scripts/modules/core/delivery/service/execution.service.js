'use strict';
let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executions.service', [
  require('../../cache/deckCacheFactory.js'),
  require('../../utils/appendTransform.js'),
  require('../../utils/lodash.js'),
  require('../../config/settings.js'),
  require('../filter/executionFilter.model.js'),
  require('./executions.transformer.service.js')
])
  .factory('executionService', function($http, $timeout, $q, $log, ExecutionFilterModel, $state,
                                        _, settings, appendTransform, executionsTransformer) {

    const activeStatuses = ['RUNNING', 'SUSPENDED', 'PAUSED', 'NOT_STARTED'];
    const runningLimit = 30;

    function getRunningExecutions(applicationName) {
      return getFilteredExecutions(applicationName, {statuses: activeStatuses, limit: runningLimit});
    }

    function getFilteredExecutions(applicationName, {statuses = Object.keys(_.pick(ExecutionFilterModel.sortFilter.status || {}, _.identity)), limit = ExecutionFilterModel.sortFilter.count} = {}) {
      let url = [ settings.gateUrl, 'applications', applicationName, `pipelines?limit=${limit}`].join('/');
      if (statuses.length) {
        url += '&statuses=' + statuses.map((status) => status.toUpperCase()).join(',');
      }
      return $http({
        method: 'GET',
        url: url,
        timeout: settings.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      })
        .then((resp) => resp.data);
    }

    function getExecutions(applicationName) {
      return getFilteredExecutions(applicationName);
    }

    function getExecution(executionId) {
      const url = [ settings.gateUrl, 'pipelines', executionId].join('/');
      return $http({
        method: 'GET',
        url: url,
        timeout: settings.pollSchedule * 2 + 5000, // TODO: replace with apiHost call
      }).then((resp) => resp.data);
    }

    function transformExecution(application, execution) {
      return executionsTransformer.transformExecution(application, execution);
    }

    function transformExecutions(application, executions) {
      if (!executions || !executions.length) {
        return;
      }
      executions.forEach((execution) => {
        let stringVal = JSON.stringify(execution);
        // do not transform if it hasn't changed
        let match = (application.executions.data || []).filter((test) => test.id === execution.id);
        if (!match.length || !match[0].stringVal || match[0].stringVal !== stringVal) {
          execution.stringVal = stringVal;
          executionsTransformer.transformExecution(application, execution);
        }
      });
    }

    function waitUntilNewTriggeredPipelineAppears(application, pipelineName, triggeredPipelineId) {

      return getRunningExecutions(application.name).then(function(executions) {
        var [match] = executions.filter(function(execution) {
          return execution.id === triggeredPipelineId;
        });
        var deferred = $q.defer();
        if (match) {
          application.executions.refresh().then(deferred.resolve);
          return deferred.promise;
        } else {
          return $timeout(function() {
            return waitUntilNewTriggeredPipelineAppears(application, pipelineName, triggeredPipelineId);
          }, 1000);
        }
      });
    }

    function waitUntilPipelineIsCancelled(application, executionId) {
      return waitUntilExecutionMatches(executionId, (execution) => execution.status === 'CANCELED')
        .then(application.executions.refresh);
    }

    function waitUntilPipelineIsDeleted(application, executionId) {
      let deferred = $q.defer();
      getExecution(executionId).then(
        () => $timeout(() => waitUntilPipelineIsDeleted(application, executionId).then(deferred.resolve), 1000),
        deferred.resolve
      );
      deferred.promise.then(application.executions.refresh);
      return deferred.promise;
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
        () => waitUntilExecutionMatches(executionId, matcher).then(application.executions.refresh).then(deferred.resolve),
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
        () => waitUntilExecutionMatches(executionId, matcher).then(application.executions.refresh).then(deferred.resolve),
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

    function addExecutionsToApplication(application, executions = []) {
      // only add executions if we actually got some executions back
      // this will fail if there was just one execution and someone just deleted it
      // but that is much less likely at this point than orca falling over under load,
      // resulting in an empty list of executions coming back
      if (application.executions.data && application.executions.data.length && executions.length) {

        // remove any that have dropped off, update any that have changed
        let toRemove = [];
        application.executions.data.forEach((execution, idx) => {
          let matches = executions.filter((test) => test.id === execution.id);
          if (!matches.length) {
            toRemove.push(idx);
          } else {
            if (execution.stringVal && matches[0].stringVal && execution.stringVal !== matches[0].stringVal) {
              application.executions.data[idx] = matches[0];
            }
          }
        });

        toRemove.reverse().forEach((idx) => application.executions.data.splice(idx, 1));

        // add any new ones
        executions.forEach((execution) => {
          if (!application.executions.data.filter((test) => test.id === execution.id).length) {
            application.executions.data.push(execution);
          }
        });
      } else {
        application.executions.data = executions;
      }
    }

    function updateExecution(application, execution) {
      if (application.executions.data && application.executions.data.length) {
        application.executions.data.forEach((t, idx) => {
          if (execution.id === t.id) {
            execution.stringVal = JSON.stringify(execution);
            if (t.stringVal !== execution.stringVal) {
              transformExecution(application, execution);
              application.executions.data[idx] = execution;
              application.executions.refreshStream.onNext();
            }
          }
        });
      }
    }

    function getLastExecutionForApplicationByConfigId(appName, configId) {
      return getFilteredExecutions(appName, {}, 1 )
        .then((executions) => {
          return executions.filter((execution) => {
            return execution.pipelineConfigId === configId;
          });
        })
        .then((executionsByConfigId) => {
          return executionsByConfigId[0];
        });
    }

    function patchExecution(executionId, stageId, data) {
      var targetUrl = [settings.gateUrl, 'pipelines', executionId, 'stages', stageId].join('/');
      var request = {
        method: 'PATCH',
        url: targetUrl,
        data: data,
        timeout: settings.pollSchedule * 2 + 5000
      };
      return $http(request).then(resp => resp.data);
    }

    return {
      getExecutions: getExecutions,
      getExecution: getExecution,
      getRunningExecutions: getRunningExecutions,
      transformExecutions: transformExecutions,
      transformExecution: transformExecution,
      cancelExecution: cancelExecution,
      resumeExecution: resumeExecution,
      pauseExecution: pauseExecution,
      deleteExecution: deleteExecution,
      waitUntilNewTriggeredPipelineAppears: waitUntilNewTriggeredPipelineAppears,
      waitUntilExecutionMatches: waitUntilExecutionMatches,
      getSectionCacheKey: getSectionCacheKey,
      getProjectExecutions: getProjectExecutions,
      addExecutionsToApplication: addExecutionsToApplication,
      updateExecution: updateExecution,
      getLastExecutionForApplicationByConfigId: getLastExecutionForApplicationByConfigId,
      patchExecution: patchExecution,
    };
  });
