'use strict';


angular.module('deckApp')
  .filter('taskFilter', function() {
    return function(tasks, pred) {
      switch (pred) {
        case 'All':
          return tasks;
        case 'Running':
          return tasks.filter(function(task) {
            return task.status === 'RUNNING';
          });
        case 'Completed':
          return tasks.filter(function(task) {
            return task.status === 'SUCCEEDED';
          });
        case 'Errored':
          return tasks.filter(function(task) {
            return task.status === 'FAILED' || task.status === 'TERMINAL' || task.status === 'SUSPENDED';
          });
        default:
          return tasks;
      }
    };
  });
