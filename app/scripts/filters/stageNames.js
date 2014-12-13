'use strict';

angular.module('deckApp')
  .filter('stageNames', function() {
    return function(executions) {
      return Object.keys(executions.reduce(function(acc, cur) {
        cur.stages.forEach(function(stage) {
          acc[stage.name] = true;
        });
        return acc;
      }, {}));
    };
  });
