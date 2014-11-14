'use strict';

angular.module('deckApp')
  .filter('groupPipelinesBy', function() {
    var ret = {};
    return function(executions, key) {
      Object.keys(ret).forEach(function(key) {
        delete ret[key];
      });
      return executions.reduce(function(acc, curr) {
        if (angular.isArray(acc[curr[key]])) {
          acc[curr[key]].push(curr);
        } else {
          acc[curr[key]] = [curr];
        }
        return acc;
      }, ret);
    };
  
  });
