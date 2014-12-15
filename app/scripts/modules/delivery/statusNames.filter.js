'use strict';

angular.module('deckApp.delivery')
  .filter('statusNames', function() {
    return function(names) {
      // hack to handle both terminal & failed states
      var ret = angular.copy(names);
      delete names.terminal;
      return ret;
    };
  });
