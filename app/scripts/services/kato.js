'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('kato', function(settings, Restangular, $timeout, $q, _) {

    function setTaskProperties(task) {
      task.waitUntilComplete = function waitUntilComplete() {
        var deferred = $q.defer();
        $timeout(function() {
          deferred.notify(task);
        });
        if (task.isRunning) {
          $timeout(function() {
            task.get().then(function(updatedTask) {
              updatedTask.waitUntilComplete().then(deferred.resolve, deferred.reject, deferred.notify);
            });
          }, 300);
        }
        if (task.isCompleted && !task.isFailed) {
          deferred.resolve(task);
        }
        if (task.isFailed) {
          deferred.reject(task);
        }
        return deferred.promise;
      };

      task.asPondKatoTask = function asPondKatoTask() {
        var pondTask = {
          history: task.history,
          status: task.status,
          resultObjects: task.resultObjects
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
        RestangularConfigurer.setBaseUrl(settings.katoUrl);
        RestangularConfigurer.addElementTransformer('task', false, function(task) {
          setTaskProperties(task);
          return task;
        });
      });
    }

    return {
      getTask: function(taskId) {
        return configureRestangular().one('task', taskId);
      }
    };

  });
