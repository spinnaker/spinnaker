'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stages.core.displayableTasks.filter', [])
  .filter('displayableTasks', function() {
    var blacklist = [
      'forceCacheRefresh', 'stageStart', 'stageEnd'
    ];
    return function(input) {
      if (input) {
        return input.filter(function(test) {
          return blacklist.indexOf(test.name) === -1 ? input : null;
        });
      }
    };
  });
