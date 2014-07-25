'use strict';

angular.module('deckApp')
  .filter('step', function(dateFromTimestampFilter) {
    return function(input) {
      input.started = dateFromTimestampFilter(input.startTime);
      if (input.endtime) {
        input.ended = dateFromTimestampFilter(input.endTime);
      }
      return input;
    };

  });
