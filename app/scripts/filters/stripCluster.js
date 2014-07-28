'use strict';

angular.module('deckApp')
  .filter('stripCluster', function() {
    return function(input, cluster) {
      if (input.indexOf(cluster + '-') !== -1) {
        return input.substring(cluster.length + 1);
      }
      return 'n/a';
    };
  });
