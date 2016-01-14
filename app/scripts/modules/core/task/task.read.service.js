'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.task.read.service', [
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('../orchestratedItem/orchestratedItem.transformer.js')
  ])
  .factory('taskReader', function (Restangular, $log, $q, $timeout, orchestratedItemTransformer) {

    const activeStatuses = ['RUNNING', 'SUSPENDED', 'NOT_STARTED'];

    function getOrchestrationException(task) {
      var katoTasks = task.getValueFor('kato.tasks');
      if (katoTasks) {
        var steps = katoTasks[katoTasks.length - 1].history;
        var exception = katoTasks[katoTasks.length - 1].exception;
        if (exception) {
          return exception.message || 'No reason provided';
        }
        return steps && steps.length ? steps[steps.length - 1].status : 'No reason provided';
      }
      return null;
    }

    function getGeneralException(task) {
      var generalException = task.getValueFor('exception');
      if (generalException) {
        if (generalException.details && generalException.details.errors && generalException.details.errors.length) {
          return generalException.details.errors.join(', ');
        }
        if (generalException.details && generalException.details.error) {
          return generalException.details.error;
        }
        return 'No reason provided';
      }
      return null;
    }

    function setTaskProperties(task) {
      orchestratedItemTransformer.defineProperties(task);
      if (task.steps && task.steps.length) {
        task.steps.forEach(orchestratedItemTransformer.defineProperties);
      }

      task.getValueFor = function(key) {
        if (!task.variables) {
          return null;
        }
        var matching = task.variables.filter(function(item) {
          return item.key === key;
        });
        return matching.length > 0 ? matching[0].value : null;
      };

      Object.defineProperties(task, {
        failureMessage: {
          get: function() {
            return getGeneralException(task) || getOrchestrationException(task) || false;
          }
        },
      });
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
      original.status = updated.status;
      original.variables = updated.variables;
      original.steps = updated.steps;
      original.endTime = updated.endTime;
      original.execution = updated.execution;
      original.history = updated.history;
    }

    function waitUntilTaskMatches(application, task, closure, failureClosure) {
      let deferred = $q.defer();
      if (closure(task)) {
        deferred.resolve();
      } else if (failureClosure && failureClosure(task)) {
        deferred.reject();
      } else {
        task.poller = $timeout(() => {
            getTask(application, task.id).then((updated) => {
              updateTask(task, updated);
              waitUntilTaskMatches(application, task, closure, failureClosure)
                .then(deferred.resolve, deferred.reject);
            });
        }, 1000);
      }
      return deferred.promise;
    }

    function waitUntilTaskCompletes(application, task) {
      return waitUntilTaskMatches(application, task, (task) => task.isCompleted, (task) => task.isFailed);
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
