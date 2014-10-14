'use strict';


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
            return item.status === 'FAILED' || item.status === 'STOPPED';
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

    function updateTask(original, updated) {
      original.status = updated.status;
      original.endTime = updated.endTime;
      original.variables = updated.variables;
      if (updated.katoTasks.length) {
        var katoTasks = getKatoTasks(original);
        katoTasks[katoTasks.length - 1].history = updated.katoTasks;
      }
    }

    function setTaskProperties(task) {

      task.updateKatoTask = function(katoTask) {
        var katoTasks = getKatoTasks(task);
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
          task.get().then(function(updatedTask) {
            updateTask(task, updatedTask);
            task.getCompletedKatoTask(phase).then(deferred.resolve, deferred.reject);
          });
        };
      }

      task.getCompletedKatoTask = function(phaseFilter) {
        var deferred = $q.defer();

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

        if (running) {
          if (katoTaskId) {
            // check for new task id
            kato.getTask(katoTaskId.id).get().then(
              function(katoTask) {
                task.updateKatoTask(katoTask);
                katoTask.waitUntilComplete().then(
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
            $timeout(refreshPondTaskForKato(task, phaseFilter, deferred), 500);
          }
        }

        return deferred.promise;
      };

      task.watchForTaskComplete = function() {
        var deferred = $q.defer();
        if (task.isFailed) {
          deferred.reject(task);
        }
        if (task.isCompleted) {
          deferred.resolve(task);
        }
        if (task.isRunning) {
          $timeout(function() {
            task.get().then(function(updatedTask) {
              updatedTask.watchForTaskComplete().then(deferred.resolve, deferred.reject);
            });
          }, 500);
        }
        return deferred.promise;
      };

      task.watchForForceRefresh = function() {
        var deferred = $q.defer();
        if (task.isFailed) {
          deferred.reject(task);
        }
        if (task.isCompleted || task.isRunning) {
          var forceRefreshStep = task.steps.filter(function(step) { return step.name === 'ForceCacheRefreshStep'; });
          if (forceRefreshStep.length && forceRefreshStep[0].status !== 'STARTED' && forceRefreshStep[0].status !== 'EXECUTING') {
            if (forceRefreshStep[0].status === 'COMPLETED') {
              deferred.resolve(task);
            }
            if (forceRefreshStep[0].status === 'FAILED') {
              deferred.reject(task);
            }
          } else {
            if (task.isCompleted) {
              deferred.reject(task);
            } else {
              $timeout(function() {
                task.get().then(function(updatedTask) {
                  updatedTask.watchForForceRefresh().then(deferred.resolve, deferred.reject);
                });
              }, 500);
            }
          }
        }
        return deferred.promise;
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
              var steps = katoTasks[katoTasks.length-1].history;
              var exception = katoTasks[katoTasks.length -1].exception;
              if (exception) {
                return exception.message || 'No reason provided';
              }
              return steps[steps.length-1].status;
            }
            return null;
          }
        },
        history: {
          get: function() {
            var katoTasks = getKatoTasks(task);
            if (katoTasks) {
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
