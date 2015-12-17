'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.tasks.api', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
  require('./kato.js'),
  require('../config/settings.js'),
  require('../cache/deckCacheFactory.js'),
  require('../navigation/urlBuilder.service.js'),
  require('../orchestratedItem/orchestratedItem.transformer.js')
])
  .factory('tasksApi', function(settings, Restangular, urlBuilderService, $timeout, $q, kato, $log, orchestratedItemTransformer) {

    function getKatoTasks(task) {
      return task.getValueFor('kato.tasks');
    }

    function updateTask(original, updated) {
      original.status = updated.status;
      original.steps = updated.steps;
      original.endTime = updated.endTime;
      original.variables = updated.variables;
      if (updated.katoTasks && updated.katoTasks.length) {
        var katoTasks = getKatoTasks(original);
        katoTasks[katoTasks.length - 1].history = updated.katoTasks;
      }
    }

    function setTaskProperties(task) {

      task.pendingPolls = [];

      task.updateKatoTask = function(katoTask) {
        if (!katoTask) {
          $log.error('Error - no kato task found:', task.plain());
          return;
        }
        var katoTasks = task.getValueFor('kato.tasks');
        if (katoTasks && katoTasks.length && katoTasks[katoTasks.length - 1].id === katoTask.id) {
          var lastTask = katoTasks[katoTasks.length - 1];
          angular.copy(katoTask.asOrcaKatoTask(), lastTask);
        } else {
          if (katoTasks) {
            katoTasks.push(katoTask.asOrcaKatoTask());
          }
          task.variables.push({key: 'kato.tasks', value: [katoTask.asOrcaKatoTask()]});
        }
      };

      task.getValueFor = function(key) {
        if (!task.variables) {
          return null;
        }
        var matching = task.variables.filter(function(item) {
          return item.key === key;
        });
        return matching.length > 0 ? matching[0].value : null;
      };

      function filterToPhase(katoTasks, phase) {
        if (katoTasks && phase) {
          return katoTasks.filter(function(task) {
            return task.history.some(function(step) {
              return step.phase === phase;
            });
          });
        }
        return katoTasks;
      }

      function refreshOrcaTaskForKato(task, phase, deferred) {
        return function() {
          if (!deferred.promise.cancelled) {
            task.get().then(function (updatedTask) {
              updateTask(task, updatedTask);
              task.getCompletedKatoTask(phase, deferred).then(deferred.resolve, deferred.reject);
            });
          }
        };
      }

      task.getCompletedKatoTask = function(phaseFilter, chainedDeferred) {
        var deferred = chainedDeferred || $q.defer();

        var katoTasks = filterToPhase(getKatoTasks(task), phaseFilter);
        var katoStatus = katoTasks && katoTasks.length ? katoTasks[katoTasks.length-1].status : null;

        var failed = task.isFailed || (katoStatus && katoStatus.failed),
            succeeded = katoStatus && katoStatus.completed && !katoStatus.failed,
            running = !failed && !succeeded,
            katoTaskId = task.getValueFor('kato.last.task.id');

        if (failed) {
          var rejectWith = katoTasks ? katoTasks[katoTasks.length-1] : null;
          deferred.reject(rejectWith);
        }

        if (succeeded) {
          deferred.resolve(katoTasks[katoTasks.length-1]);
        }

        if (running && !deferred.promise.cancelled) {
          if (katoTaskId) {
            // check for new task id
            kato.getTask(task.getValueFor('application'), task.id, katoTaskId.id).get().then(
              function(katoTask) {
                task.updateKatoTask(katoTask);
                var katoWait = katoTask.waitUntilComplete();
                task.pendingPolls.push(katoWait);
                katoWait.then(
                  function(updatedKatoTask) {
                    if (filterToPhase([updatedKatoTask], phaseFilter).length && updatedKatoTask.isCompleted) {
                      deferred.resolve(updatedKatoTask);
                    } else {
                      $timeout(refreshOrcaTaskForKato(task, phaseFilter, deferred), 500);
                    }
                  },
                  deferred.reject,
                  task.updateKatoTask);
              },
              deferred.reject,
              task.updateKatoTask
            );
          } else {
            $timeout(refreshOrcaTaskForKato(task, phaseFilter, deferred), 1000);
          }
        }
        task.pendingPolls.push(deferred.promise);
        return deferred.promise;
      };

      task.watchForTaskComplete = function(chainedDeferred) {
        var deferred = chainedDeferred || $q.defer();
        if (task.isFailed) {
          deferred.reject(task);
        }
        if (task.isCompleted) {
          deferred.resolve(task);
        }
        if ( (task.isRunning || task.hasNotStarted) && !deferred.promise.cancelled) {
          $timeout(function () {
            task.get().then(function (updatedTask) {
              updateTask(task, updatedTask);
              task.watchForTaskComplete(deferred).then(deferred.resolve, deferred.reject);
            });
          }, 1000);
        }
        task.pendingPolls.push(deferred.promise);
        return deferred.promise;
      };

      task.watchForForceRefresh = function(chainedDeferred) {
        var deferred = chainedDeferred || $q.defer();
        if (task.isFailed) {
          deferred.reject(task);
        }
        if (task.isCompleted || task.isRunning) {
          var forceRefreshStep = task.steps.filter(function(step) { return step.name === 'forceCacheRefresh'; });
          if (forceRefreshStep.length && (forceRefreshStep[0].status === 'COMPLETED' || forceRefreshStep[0].status === 'FAILED')) {
            var forceRefreshStatus = forceRefreshStep[0].status;
            if (forceRefreshStatus === 'COMPLETED') {
              deferred.resolve(task);
            }
            if (forceRefreshStatus === 'FAILED') {
              deferred.reject(task);
            }
          } else {
            // HACK: If the task is completed, the steps should be completed, but sometimes Orca does not return the latest
            // status of the steps. So we'll just band-aid over that.
            if (task.isCompleted && forceRefreshStep.length && forceRefreshStep[0].status !== 'NOT_STARTED' && forceRefreshStep[0].status !== 'RUNNING') {
              deferred.reject(task);
            } else {
              if (!deferred.promise.cancelled) {
                $timeout(function () {
                  task.get().then(function (updatedTask) {
                    updateTask(task, updatedTask);
                    task.watchForForceRefresh(deferred).then(deferred.resolve, deferred.reject);
                  });
                }, 500);
              }
            }
          }
        }
        task.pendingPolls.push(deferred.promise);
        return deferred.promise;
      };

      task.cancelPolls = function cancelPolls() {
        task.pendingPolls.forEach(function(pendingPromise) {
          pendingPromise.cancelled = true;
        });
      };

      Object.defineProperties(task, {
        katoTasks: {
          get: function() {
            if (getKatoTasks(task)) {
              var katoTasks = getKatoTasks(task);
              return katoTasks[katoTasks.length -1].history;
            }
            return [];
          }
        },
        failureMessage: {
          get: function() {
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

            if ((task.isFailed || task.isStopped) && getKatoTasks(task)) {
              var katoTasks = getKatoTasks(task);
              var katoException = katoTasks[katoTasks.length -1].exception;
              if (katoException) {
                return katoException ? katoException.message : 'No reason provided.';
              } else {
                return this.lastKatoMessage;
              }

            }

            return false;
          }
        },
        href: {
          get: function() {
            return task.isCompleted ? urlBuilderService.buildFromTask(task) : false;
          }
        },
        lastKatoMessage: {
          get: function() {
            var katoTasks = getKatoTasks(task);
            if (katoTasks) {
              var steps = katoTasks[katoTasks.length-1].history;
              var exception = katoTasks[katoTasks.length -1].exception;
              if (exception) {
                return exception.message || 'No reason provided';
              }
              return steps && steps.length ? steps[steps.length-1].status : 'No status available';
            }
            return null;
          }
        },
        history: {
          get: function() {
            var katoTasks = getKatoTasks(task);
            if (katoTasks && katoTasks.length) {
              return katoTasks[katoTasks.length-1].history;
            }
            return null;
          }
        }
      });
    }

    function configureRestangular() {
      return Restangular.withConfig(function(RestangularConfigurer) {
        RestangularConfigurer.addElementTransformer('tasks', false, function(task) {

          orchestratedItemTransformer.defineProperties(task);
          if (task.steps && task.steps.length) {
            task.steps.forEach(orchestratedItemTransformer.defineProperties);
          }
          setTaskProperties(task);

          return task;
        });
      });
    }

    return configureRestangular();

  });
