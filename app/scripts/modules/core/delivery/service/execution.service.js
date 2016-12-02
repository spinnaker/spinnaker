'use strict';

import _ from 'lodash';
import {API_SERVICE} from 'core/api/api.service';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.delivery.executions.service', [
  require('../../utils/appendTransform.js'),
  require('../../config/settings.js'),
  require('../filter/executionFilter.model.js'),
  require('./executions.transformer.service.js'),
  API_SERVICE
])
  .factory('executionService', function($http, API, $timeout, $q, $log, ExecutionFilterModel, $state,
                                        settings, appendTransform, executionsTransformer) {

    const activeStatuses = ['RUNNING', 'SUSPENDED', 'PAUSED', 'NOT_STARTED'];
    const runningLimit = 30;

    function getRunningExecutions(applicationName) {
      return getFilteredExecutions(applicationName, {statuses: activeStatuses, limit: runningLimit});
    }

    function getFilteredExecutions(applicationName, {statuses = Object.keys(_.pickBy(ExecutionFilterModel.sortFilter.status || {}, _.identity)), limit = ExecutionFilterModel.sortFilter.count} = {}) {
      let statusString = statuses.map((status) => status.toUpperCase()).join(',') || null;
      return API.one('applications', applicationName).all('pipelines').getList({ limit: limit, statuses: statusString})
        .then(data => {
          if (data) {
            data.forEach(cleanExecutionForDiffing);
            return data;
          }
          return [];
        });
    }

    function getExecutions(applicationName) {
      return getFilteredExecutions(applicationName);
    }

    function getExecution(executionId) {
      return API.one('pipelines', executionId).get()
        .then(execution => {
          cleanExecutionForDiffing(execution);
          return execution;
        });
    }

    function transformExecution(application, execution) {
      return executionsTransformer.transformExecution(application, execution);
    }

    function transformExecutions(application, executions) {
      if (!executions || !executions.length) {
        return;
      }
      executions.forEach((execution) => {
        let stringVal = JSON.stringify(execution, jsonReplacer);
        // do not transform if it hasn't changed
        let match = (application.executions.data || []).find((test) => test.id === execution.id);
        if (!match || !match.stringVal || match.stringVal !== stringVal) {
          execution.stringVal = stringVal;
          executionsTransformer.transformExecution(application, execution);
        }
      });
    }

    function cleanExecutionForDiffing(execution) {
      (execution.stages || []).forEach(removeInstances);
      if (execution.trigger && execution.trigger.parentExecution) {
        (execution.trigger.parentExecution.stages || []).forEach(removeInstances);
      }
    }

    // these fields are never displayed in the UI, so don't retain references to them, as they consume a lot of memory
    // on very large deployments
    function removeInstances(stage) {
      if (stage.context) {
        delete stage.context.instances;
        delete stage.context.asg;
        if (stage.context.targetReferences) {
          stage.context.targetReferences.forEach(tr => {
            delete tr.instances;
            delete tr.asg;
          });
        }
      }
    }

    // remove these fields - they are not of interest when determining if the pipeline has changed
    function jsonReplacer(key, value) {
      var val = value;

      if (key === 'instances' || key === 'asg' || key === 'commits' || key === 'history' || key === '$$hashKey') {
        val = undefined;
      }

      return val;
    }

    function waitUntilNewTriggeredPipelineAppears(application, pipelineName, triggeredPipelineId) {

      return getRunningExecutions(application.name).then(function(executions) {
        let match = executions.find(function(execution) {
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
        .then(() => application.executions.refresh());
    }

    function waitUntilPipelineIsDeleted(application, executionId) {
      let deferred = $q.defer();
      getExecution(executionId).then(
        () => $timeout(() => waitUntilPipelineIsDeleted(application, executionId).then(deferred.resolve), 1000),
        deferred.resolve
      );
      deferred.promise.then(() => application.executions.refresh());
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
        () => waitUntilExecutionMatches(executionId, matcher).then(() => application.executions.refresh()).then(deferred.resolve),
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
        () => waitUntilExecutionMatches(executionId, matcher).then(() => application.executions.refresh()).then(deferred.resolve),
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
      return API.one('projects', project).all('pipelines').getList({ limit: limit })
        .then(executions => {
          if (!executions || !executions.length) {
            return [];
          }
          executions.forEach(execution => executionsTransformer.transformExecution({}, execution));
          return executions.sort((a, b) => b.startTime - (a.startTime || Date.now()));
        });
    }

    function addExecutionsToApplication(application, executions = []) {
      // only add executions if we actually got some executions back
      // this will fail if there was just one execution and someone just deleted it
      // but that is much less likely at this point than orca falling over under load,
      // resulting in an empty list of executions coming back
      if (application.executions.data && application.executions.data.length && executions.length) {
        let existingData = application.executions.data;
        // remove any that have dropped off, update any that have changed
        let toRemove = [];
        existingData.forEach((execution, idx) => {
          let match = executions.find((test) => test.id === execution.id);
          if (!match) {
            toRemove.push(idx);
          } else {
            if (execution.stringVal && match.stringVal && execution.stringVal !== match.stringVal) {
              if (execution.status !== match.status) {
                application.executions.data[idx] = match;
              } else {
                synchronizeExecution(execution, match);
              }
            }
          }
        });

        toRemove.reverse().forEach((idx) => existingData.splice(idx, 1));

        // add any new ones
        executions.forEach((execution) => {
          if (!existingData.filter((test) => test.id === execution.id).length) {
            existingData.push(execution);
          }
        });
        return existingData;
      } else {
        return executions;
      }
    }

    function synchronizeExecution(current, updated) {
      (updated.stageSummaries || []).forEach((updatedSummary, idx) => {
        let currentSummary = current.stageSummaries[idx];
        // if the stage was not already completed, update it in place if it has changed to save Angular
        // from removing, then re-rendering every DOM node
        if (!updatedSummary.isComplete || !current.isComplete) {
          if (JSON.stringify(current, jsonReplacer) !== JSON.stringify(updatedSummary, jsonReplacer)) {
            Object.assign(currentSummary, updatedSummary);
          }
        }
        current.stringVal = updated.stringVal;
      });
    }

    function updateExecution(application, updatedExecution) {
      if (application.executions.data && application.executions.data.length) {
        application.executions.data.forEach((currentExecution, idx) => {
          if (updatedExecution.id === currentExecution.id) {
            updatedExecution.stringVal = JSON.stringify(updatedExecution, jsonReplacer);
            if (updatedExecution.status !== currentExecution.status) {
              transformExecution(application, updatedExecution);
              application.executions.data[idx] = updatedExecution;
              application.executions.dataUpdated();
            } else {
              if (currentExecution.stringVal !== updatedExecution.stringVal) {
                transformExecution(application, updatedExecution);
                synchronizeExecution(currentExecution, updatedExecution);
                application.executions.dataUpdated();
              }
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
      synchronizeExecution: synchronizeExecution,
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
