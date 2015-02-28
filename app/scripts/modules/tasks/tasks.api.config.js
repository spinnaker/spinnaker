'use strict';


angular.module('deckApp.tasks.api', [
  'restangular',
  'deckApp.kato.service',
  'deckApp.settings',
  'deckApp.urlBuilder',
  'deckApp.orchestratedItem.service'
])
  .factory('tasksApi', function(settings, Restangular, urlBuilder, $timeout, $q, kato, $exceptionHandler, orchestratedItem) {

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
          $exceptionHandler('Error - no kato task found:', task.plain());
          return;
        }
        var katoTasks = task.getValueFor('kato.tasks');
        if (katoTasks && katoTasks.length && katoTasks[katoTasks.length - 1].id === katoTask.id) {
          var lastTask = katoTasks[katoTasks.length - 1];
          angular.copy(katoTask.asPondKatoTask(), lastTask);
        } else {
          if (katoTasks) {
            katoTasks.push(katoTask.asPondKatoTask());
          }
          task.variables.push({key: 'kato.tasks', value: [katoTask.asPondKatoTask()]});
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

      function refreshPondTaskForKato(task, phase, deferred) {
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
                      $timeout(refreshPondTaskForKato(task, phaseFilter, deferred), 500);
                    }
                  },
                  deferred.reject,
                  task.updateKatoTask);
              },
              deferred.reject,
              task.updateKatoTask
            );
          } else {
            $timeout(refreshPondTaskForKato(task, phaseFilter, deferred), 1000);
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
          if (forceRefreshStep.length && forceRefreshStep[0].status !== 'RUNNING') {
            var forceRefreshStatus = forceRefreshStep[0].status;
            if (forceRefreshStatus === 'COMPLETED') {
              deferred.resolve(task);
            }
            if (forceRefreshStatus === 'FAILED') {
              deferred.reject(task);
            }
          } else {
            if (task.isCompleted) {
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
              return generalException.details.errors ? generalException.details.errors.join(', ') : 'No reason provided';
            }

            if ((task.isFailed || task.isStopped) && getKatoTasks(task)) {
              var katoTasks = getKatoTasks(task);
              var katoException = katoTasks[katoTasks.length -1].exception;
              return katoException ? katoException.message : 'No reason provided';
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

    function setTaskCollectionProperties(taskCollection) {
      Object.defineProperties(taskCollection, {
        runningCount: {
          get: function() {
            return taskCollection.reduce(function(acc, current) {
              return ['NOT_STARTED', 'RUNNING'].indexOf(current.status) !== -1 ? acc + 1 : acc;
            }, 0);
          }
        }
      });
    }

    function configureRestangular() {
      return Restangular.withConfig(function(RestangularConfigurer) {
        RestangularConfigurer.addElementTransformer('tasks', true, function(taskCollection) {
          setTaskCollectionProperties(taskCollection);
          return taskCollection;
        });
        RestangularConfigurer.addElementTransformer('tasks', false, function(task) {

          orchestratedItem.defineProperties(task);
          if (task.steps && task.steps.length) {
            task.steps.forEach(orchestratedItem.defineProperties);
          }
          setTaskProperties(task);

          return task;
        });
      });
    }

    return configureRestangular();

  });
