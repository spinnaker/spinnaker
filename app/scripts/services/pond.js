'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('pond', function(settings, Restangular, momentService, urlBuilder) {
    function setStatusProperties(item) {
      Object.defineProperties(item, {
        isCompleted: {
          get: function() {
            return item.status === 'COMPLETED';
          },
        },
        isRunning: {
          get: function() {
            return item.status === 'RUNNING';
          },
        },
        isFailed: {
          get: function() {
            return item.status === 'FAILED';
          },
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

    return Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.pondUrl);
      RestangularConfigurer.addElementTransformer('tasks', true, function(taskCollection) {
        Object.defineProperties(taskCollection, {
          runningCount: {
            get: function() {
              return taskCollection.reduce(function(acc, current) {
                return current.status === 'STARTED' ? acc + 1 : acc;
              }, 0);
            }
          }
        });
        return taskCollection;
      });
      RestangularConfigurer.addElementTransformer('tasks', false, function(task) {
        task.getValueFor = function(key) {
          var matching = task.variables.filter(function(item) {
            return item.key === key;
          });
          return matching.length > 0 ? matching[0].value : null;
        };

        setStatusProperties(task);
        task.steps.forEach(setStatusProperties);

        Object.defineProperties(task, {
          katoTasks: {
            get: function() {
              if (task.getValueFor('kato.tasks')) {
                var katoTasks = task.getValueFor('kato.tasks');
                var katoSteps = katoTasks[katoTasks.length -1].history;
                return katoSteps;
              }
              return [];
            },
          },
          failureMessage: {
            get: function() {
              if (task.isFailed && task.getValueFor('kato.tasks')) {
                var katoTasks = task.getValueFor('kato.tasks');
                var katoSteps = katoTasks[katoTasks.length -1].history;
                return katoSteps[katoSteps.length -1].status;
              }
              return false;
            },
          },
          href: {
            get: function() {
              return task.isCompleted ? urlBuilder.buildFromTask(task) : false;
            },
          },
        });

        return task;
      });
    });
  });
