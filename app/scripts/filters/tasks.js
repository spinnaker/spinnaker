'use strict';

angular.module('deckApp')
  .filter('taskFilter', function() {
    return function(tasks, pred) {
      switch (pred) {
        case 'All':
          return tasks;
        case 'Running':
          return tasks.filter(function(task) {
            return task.status === 'STARTED';
          });
        case 'Completed':
          return tasks.filter(function(task) {
            return task.status === 'COMPLETED';
          });
        case 'Errored':
          return tasks.filter(function(task) {
            return task.status === 'FAILED';
          });
        default:
          return tasks;
      }
    };
  });
