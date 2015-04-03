'use strict';

angular.module('deckApp.pipelines.stages.core.displayableTasks.filter', [])
  .filter('displayableTasks', function() {
    var blacklist = [
      'forceCacheRefresh',
    ];
    return function(input) {
      if (input) {
        return input.filter(function(test) {
          return blacklist.indexOf(test.name) === -1 ? input : null;
        });
      }
    };
  });
