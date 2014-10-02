'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('pond', function(settings, Restangular, momentService, urlBuilder, $timeout, $q) {
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
        var katoTasks = getKatoTasks(task);
        var katoStatus = katoTasks ? katoTasks[katoTasks.length-1].status : null;
        if (katoStatus) {
          if (katoStatus.failed) {
            return $q.reject(task);
          }
          if (katoStatus.completed && !katoStatus.failed) {
            return $q.when(task);
          }
        } else {
          return $timeout(function() {
            return task.get().then(function(updatedTask) {
              return updatedTask.watchForKatoCompletion();
            });
          }, 300);
        }
      };

      task.watchForForceRefresh = function() {
        if (task.isFailed) {
          return $q.reject(task);
        }
        if (task.isCompleted) {
          var forceRefreshStep = task.steps.filter(function(step) { return step.name === 'ForceCacheRefreshStep'; });
          if (forceRefreshStep.length && forceRefreshStep[0].status === 'COMPLETED') {
            return $q.when(task);
          } else {
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
