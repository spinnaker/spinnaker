'use strict';

module.exports = function() {
  return function(executions) {
    return Object.keys(executions.reduce(function(acc, cur) {
      cur.stages.forEach(function(stage) {
        acc[stage.name] = true;
      });
      return acc;
    }, {}));
  };
};
