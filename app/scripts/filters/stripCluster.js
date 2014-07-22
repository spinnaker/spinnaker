'use strict';

angular.module('deckApp')
  .filter('stripCluster', function() {
    return function(input, cluster) {
      if (input.indexOf(cluster.name + '-') !== -1) {
        return input.substring(cluster.name.length + 1);
      }
      return 'n/a';
    };
  });
