'use strict';

angular.module('deckApp')
  .factory('sortingService', function() {

  function asgSorter(a, b) {
    var av = a.name.split('-').pop(),
      bv = b.name.split('-').pop();
    if (av.indexOf('v') === -1 || bv.indexOf('v') === -1 || isNaN(av.substring(1)) || isNaN(bv.substring(1))) {
      return av - bv;
    } else {
      return parseInt(av.substring(1)) - parseInt(bv.substring(1));
    }
  }

  return {
    asgSorter: asgSorter
  };
});
