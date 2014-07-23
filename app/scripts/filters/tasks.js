'use strict';

angular.module('deckApp')
  .filter('taskFilter', function() {
    return function(tasks, pred) {
      switch (pred) {
        case 'All':
          return tasks;
        case 'Running':
          return tasks.filter(function(task) {
            return !task.$done;
          });
        case 'Completed':
          return tasks.filter(function(task) {
            return task.$done;
          });
        case 'Errored':
          return tasks.filter(function(task) {
            return task.$done && !task.$success;
          });
        default:
          return tasks;
      }
    };
  });
