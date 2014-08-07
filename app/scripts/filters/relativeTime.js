'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .filter('relativeTime', function(momentService) {
    return function(input) {
      var moment = momentService(input);
      return moment.isValid()? moment.calendar() : 'n/a';
    };
  });
