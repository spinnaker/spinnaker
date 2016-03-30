'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.task.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../orchestratedItem/orchestratedItem.transformer.js')
  ])
  .factory('taskReader', function (Restangular, $log, $q, $timeout, orchestratedItemTransformer) {

    const activeStatuses = ['RUNNING', 'SUSPENDED', 'NOT_STARTED'];

    function setTaskProperties(task) {
      orchestratedItemTransformer.defineProperties(task);
      if (task.steps && task.steps.length) {
        task.steps.forEach(orchestratedItemTransformer.defineProperties);
      }
    }

    function getRunningTasks(applicationName) {
      return getTasks(applicationName, activeStatuses);
    }

    function getTasks(applicationName, statuses = []) {
      return Restangular.one('applications', applicationName).all('tasks')
        .getList({statuses: statuses.join(',')})
        .then((tasks) => {
          tasks.forEach(setTaskProperties);
          return tasks.filter((task) => !task.getValueFor('dryRun'));
        });
    }

    /**
     * When polling for a match, (most of) the new task's properties are copied into the original task; if you need
     * some other property, you'll need to update this method
     */
    function updateTask(original, updated) {
      if (!updated) {
        return;
      }
      original.status = updated.status;
      original.variables = updated.variables;
      original.steps = updated.steps;
      original.endTime = updated.endTime;
      original.execution = updated.execution;
      original.history = updated.history;
    }

    function waitUntilTaskMatches(application, task, closure, failureClosure, interval = 1000) {
      let deferred = $q.defer();
      if (!task) {
        deferred.reject();
      } else if (closure(task)) {
        deferred.resolve(task);
      } else if (failureClosure && failureClosure(task)) {
        deferred.reject(task);
      } else {
        task.poller = $timeout(() => {
          getTask(application, task.id).then((updated) => {
            updateTask(task, updated);
            waitUntilTaskMatches(application, task, closure, failureClosure, interval)
              .then(deferred.resolve, deferred.reject);
          });
        }, interval);
      }
      return deferred.promise;
    }

    function waitUntilTaskCompletes(application, task, interval = 1000) {
      return waitUntilTaskMatches(application, task, (task) => task.isCompleted, (task) => task.isFailed, interval);
    }

    function getTask(applicationName, taskId) {
      return Restangular.one('applications', applicationName).one('tasks', taskId).get()
        .then((task) => {
          orchestratedItemTransformer.defineProperties(task);
          if (task.steps && task.steps.length) {
            task.steps.forEach(orchestratedItemTransformer.defineProperties);
          }
          if (task.execution) {
            orchestratedItemTransformer.defineProperties(task.execution);
            if (task.execution.stages) {
              task.execution.stages.forEach(orchestratedItemTransformer.defineProperties);
            }
          }
          let plainTask = task.plain();
          setTaskProperties(plainTask);
          return plainTask;
        })
        .catch((error) => $log.warn('There was an issue retrieving taskId: ', taskId, error));
    }

    return {
      getTasks: getTasks,
      getRunningTasks: getRunningTasks,
      getTask: getTask,
      waitUntilTaskMatches: waitUntilTaskMatches,
      waitUntilTaskCompletes: waitUntilTaskCompletes,
    };

  });
