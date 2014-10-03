'use strict';

angular.module('deckApp')
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
