'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.search.searchResult.searchRank.filter', [])
  .filter('searchRank', function() {
    return (input, query) => {
      query = query.toLowerCase();
      if (input && input.length) {
        var result = input.slice();
        result.sort(function (a, b) {
          if (a.displayName && b.displayName) {
            let aIndex = a.displayName.toLowerCase().indexOf(query),
                bIndex = b.displayName.toLowerCase().indexOf(query);
            return aIndex === bIndex ? a.displayName.localeCompare(b.displayName) : aIndex - bIndex;
          }
          return 0;
        });
        return result;
      }
    };
  });
