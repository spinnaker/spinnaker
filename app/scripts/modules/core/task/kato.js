'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.task.kato.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('../utils/lodash.js'),
  require('../cache/deckCacheFactory.js'),
])
  .factory('kato', function(settings, Restangular, $timeout, $q, _) {

    function updateTask(original, updated) {
      original.status = updated.status;
      original.history = updated.history;
      original.resultObjects = updated.resultObjects;
    }

    function setTaskProperties(task) {
      task.waitUntilComplete = function waitUntilComplete(chainedDeferred) {
        var deferred = chainedDeferred || $q.defer();
        $timeout(function() {
          deferred.notify(task);
        });
        if (task.isRunning && !deferred.promise.cancelled) {
          $timeout(function() {
            task.get().then(function(updatedTask) {
              updateTask(task, updatedTask);
              task.waitUntilComplete(deferred).then(deferred.resolve, deferred.reject);
            });
          }, 500);
        }
        if (task.isCompleted && !task.isFailed) {
          deferred.resolve(task);
        }
        if (task.isFailed) {
          deferred.reject(task);
        }
        return deferred.promise;
      };

      task.asOrcaKatoTask = function asOrcaKatoTask() {
        var pondTask = {
          history: task.history,
          status: task.status,
          resultObjects: task.resultObjects,
          id: task.id
        };

        var exception = _.find(task.resultObjects, {type: 'EXCEPTION'});
        if (exception) {
          pondTask.exception = exception;
        }
        return pondTask;
      };

      Object.defineProperties(task, {
        isRunning: {
          get: function() {
            return task.status ? !task.status.completed : true;
          }
        },
        isFailed: {
          get: function() {
            return !task.status || task.status.failed;
          }
        },
        isCompleted: {
          get: function() {
            return task.status ? task.status.completed : true;
          }
        },
        statusMessage: {
          get: function() {
            return task.status ? task.status.status : null;
          }
        }
      });
    }

    function configureRestangular() {
      return Restangular.withConfig(function(RestangularConfigurer) {
        RestangularConfigurer.addElementTransformer('details', false, function(task) {
          setTaskProperties(task);
          return task;
        });
      });
    }

    return {
      getTask: function(application, taskId, taskDetailsId) {
        return configureRestangular().all('applications').one(application).one('tasks', taskId).one('details', taskDetailsId);
      }
    };

  });
