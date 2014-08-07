'use strict';

require('../app');
var angular = require('angular');

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
