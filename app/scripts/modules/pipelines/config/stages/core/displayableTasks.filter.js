'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.pipelines.stages.core.displayableTasks.filter', [])
  .filter('displayableTasks', function() {
    var blacklist = [
      // TODO: Use the first line once FCR times get back to normal
      //'forceCacheRefresh', 'stageStart', 'stageEnd'
      'stageStart', 'stageEnd'
    ];
    return function(input) {
      if (input) {
        return input.filter(function(test) {
          return blacklist.indexOf(test.name) === -1 ? input : null;
        });
      }
    };
  }).name;
