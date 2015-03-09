'use strict';


angular.module('deckApp')
  .filter('timestamp', function(momentService) {
    return function(input) {
      var moment = momentService(isNaN(parseInt(input)) ? input : parseInt(input));
      return moment.isValid() ? moment.format('YYYY-MM-DD HH:mm:ss') : 'n/a';
    };
  })
  .filter('relativeTime', function(momentService) {
    return function(input) {
      var moment = momentService(isNaN(parseInt(input)) ? input : parseInt(input));
      return moment.isValid() ? moment.fromNow() : 'n/a';
    };
  })
  .filter('duration', function(momentService) {
    return function(input) {
      var moment = momentService.utc(isNaN(parseInt(input)) ? input : parseInt(input));
      var format = moment.hours() ? 'HH:mm:ss' : 'mm:ss';
      return moment.isValid() ? moment.format(format) : 'n/a';
    };
  });
