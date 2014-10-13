'use strict';


angular.module('deckApp')
  .filter('relativeTime', function(momentService) {
    return function(input) {
      var moment = momentService(Number.isNaN(parseInt(input)) ? input : parseInt(input));
      return moment.isValid()? moment.calendar() : 'n/a';
    };
  });
