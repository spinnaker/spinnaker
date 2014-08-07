'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .filter('dateFromTimestamp', function () {
    return function (input) {
      if (input) {
        var date = new Date(0);
        date.setUTCMilliseconds(parseInt(input));
        return date;
      } else {
        return 'n/a';
      }
    };
  }
);
