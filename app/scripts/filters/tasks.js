'use strict';

angular.module('deckApp')
  .filter('taskFilter', function() {
    return function(tasks, pred) {
      switch (pred) {
        case 'All':
          return tasks;
          break;
        case 'Running':
          return tasks.filter(function(task) {
            return !task.$done;
          });
          break;
        case 'Completed':
          return tasks.filter(function(task) {
            return task.$done;
          });
          break;
        case 'Errored':
          return tasks.filter(function(task) {
            return task.$done && !task.$success;
          });
          break;
        default:
          return tasks;
          break;
      }
    };
  });
