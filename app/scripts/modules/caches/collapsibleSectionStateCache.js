'use strict';

angular.module('deckApp.caches.collapsibleSectionState', [])
  .factory('collapsibleSectionStateCache', function() {

    var stateCache = {};

    return {
      isSet: function(heading) {
        return stateCache[heading] !== undefined;
      },
      isExpanded: function(heading) {
        return stateCache[heading] === true;
      },
      setExpanded: function(heading, expanded) {
        stateCache[heading] = !!expanded;
      }
    };
  });
