'use strict';


angular.module('deckApp.utils.timeFormatters', [
  'deckApp.utils.moment',
])
  .filter('timestamp', function(momentService) {
    return function(input) {
      var moment = momentService(isNaN(parseInt(input)) ? input : parseInt(input));
      return moment.isValid() ? moment.format('YYYY-MM-DD HH:mm:ss') : '-';
    };
  })
  .filter('relativeTime', function(momentService) {
    return function(input) {
      var moment = momentService(isNaN(parseInt(input)) ? input : parseInt(input));
      return moment.isValid() ? moment.fromNow() : '-';
    };
  })
  .filter('duration', function(momentService) {
    return function(input) {
      var moment = momentService.utc(isNaN(parseInt(input)) ? input : parseInt(input));
      var format = moment.hours() ? 'HH:mm:ss' : 'mm:ss';
      var dayLabel = '';
      if(moment.isValid()) {
        var days = Math.floor(input / 86400000);
        if (days > 0) {
          dayLabel = days + 'd';
        }
      }
      return moment.isValid() ? dayLabel +  moment.format(format): '-';
    };
  })
  .filter('conformTime', function (momentService) {
    return function (input) {
      var moment = momentService(input);
      return moment.format('YYYY-MM-DD HH:mm:ss');
    };
  })
  .filter('fastPropertyTime', function(momentService) {
    return function (input) {
      var realTime = input.replace('[GMT]', '');
      var moment = momentService(realTime);
      return moment.format('MM/DD/YY @ h:mma');
    };
  });
