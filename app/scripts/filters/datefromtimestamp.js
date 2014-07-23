'use strict';

angular.module('deckApp')
  .filter('dateFromTimestamp', function() {
    return function(input) {
      var date = new Date(0);
      date.setUTCMilliseconds(parseInt(input));
      return date;
    };
  });
