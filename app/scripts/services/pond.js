'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('pond', function(settings, Restangular, momentService, urlBuilder, $timeout, $q, kato) {
    function setStatusProperties(item) {
      Object.defineProperties(item, {
        isCompleted: {
          get: function() {
            return item.status === 'COMPLETED';
          },
        },
        isRunning: {
          get: function() {
            return item.status === 'STARTED';
          },
        },
        isFailed: {
          get: function() {
            return item.status === 'FAILED';
          },
        },
        isStopped: {
          get: function() {
            return item.status === 'STOPPED';
          }
        },
        runningTime: {
          get: function() {
            return momentService
              .duration(parseInt(item.endTime) - parseInt(item.startTime))
              .humanize();
          }
        }
      });
    }

    function getKatoTasks(task) {
      return task.getValueFor('kato.tasks');
    }

    function setTaskProperties(task) {

      task.getValueFor = function(key) {
        var matching = task.variables.filter(function(item) {
          return item.key === key;
        });
        return matching.length > 0 ? matching[0].value : null;
      };

      task.watchForKatoCompletion = function() {
        var deferred = $q.defer();

        var katoTasks = getKatoTasks(task);
        var katoStatus = katoTasks ? katoTasks[katoTasks.length-1].status : null;
        var failed = katoStatus && katoStatus.failed,
          succeeded = katoStatus && katoStatus.completed && !katoStatus.failed,
          running = !failed && !succeeded;

        if (failed) {
          deferred.reject(task);
        }
        if (succeeded) {
          deferred.resolve(task);
        }
        if (running) {
          var katoTaskId = task.getValueFor('kato.last.task.id');
          if (katoTaskId) {
            kato.one('task', katoTaskId.id).get().then(function(katoTask) {
              katoTask.waitUntilComplete().then(deferred.resolve, deferred.reject, deferred.notify);
            });
          } else {
            $timeout(function() {
              task.get().then(function(updatedTask) {
                updatedTask.watchForKatoCompletion().then(deferred.resolve, deferred.reject, deferred.notify);
              });
            }, 250);
          }
        }

        return deferred.promise;
      };

      task.watchForForceRefresh = function() {
        if (task.isFailed) {
          return $q.reject(task);
        }
        var forceRefreshStep = task.steps.filter(function(step) { return step.name === 'ForceCacheRefreshStep'; });
        if (forceRefreshStep.length && forceRefreshStep[0].status === 'COMPLETED') {
          return $q.when(task);
        } else {
          if (task.isCompleted) {
            return $q.reject(task);
          }
        }
        return $timeout(function() {
          return task.get().then(function(updatedTask) {
            return updatedTask.watchForForceRefresh();
          });
        }, 500);
      };

      Object.defineProperties(task, {
        katoTasks: {
          get: function() {
            if (getKatoTasks(task)) {
              var katoTasks = getKatoTasks(task);
              var katoSteps = katoTasks[katoTasks.length -1].history;
              return katoSteps;
            }
            return [];
          }
        },
        failureMessage: {
          get: function() {
            if ((task.isFailed || task.isStopped) && getKatoTasks(task)) {
              var katoTasks = getKatoTasks(task);
              var exception = katoTasks[katoTasks.length -1].exception;
              return exception ? exception.message : 'No reason provided';
            }
            return false;
          }
        },
        href: {
          get: function() {
            return task.isCompleted ? urlBuilder.buildFromTask(task) : false;
          }
        },
        lastKatoMessage: {
          get: function() {
            var katoTasks = getKatoTasks(task);
            if (katoTasks) {
              debugger;
              var steps = katoTasks[katoTasks.length-1].history;
              return steps[steps.length-1].status;
            }
            return null;
          }
        }
      });
    }

    function setTaskCollectionProperties(taskCollection) {
      Object.defineProperties(taskCollection, {
        runningCount: {
          get: function() {
            return taskCollection.reduce(function(acc, current) {
              return current.status === 'STARTED' ? acc + 1 : acc;
            }, 0);
          }
        }
      });
    }

    function configureRestangular() {
      return Restangular.withConfig(function(RestangularConfigurer) {
        RestangularConfigurer.setBaseUrl(settings.pondUrl);
        RestangularConfigurer.addElementTransformer('tasks', true, function(taskCollection) {
          setTaskCollectionProperties(taskCollection);
          return taskCollection;
        });
        RestangularConfigurer.addElementTransformer('tasks', false, function(task) {
          setStatusProperties(task);
          if (task.steps && task.steps.length) {
            task.steps.forEach(setStatusProperties);
          }
          setTaskProperties(task);

          return task;
        });
      });
    }

    return configureRestangular();

  });
