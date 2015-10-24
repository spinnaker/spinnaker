'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.timeFormatters', [
  require('./moment.js'),
  require('../config/settings.js'),
])
  .filter('timestamp', function(momentService, settings) {
    return function(input) {
      var tz = settings.defaultTimeZone || 'America/Los_Angeles';
      if (!input) {
        return '-';
      }
      var moment = momentService.tz(isNaN(parseInt(input)) ? input : parseInt(input), tz);
      return moment.isValid() ? moment.format('YYYY-MM-DD HH:mm:ss z') : '-';
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
  .filter('fastPropertyTime', function(momentService) {
    return function (input) {
      if(input) {
        var realTime = input.replace('[GMT]', '');
        var moment = momentService(realTime);
        return moment.format('MM/DD/YY @ h:mma');
      } else {
        return '--';
      }
    };
  })
  .filter('timePickerTime', function() {
    return function (input) {
      if (input && !isNaN(input.hours) && !isNaN(input.minutes)) {
        var hours = parseInt(input.hours),
            minutes = parseInt(input.minutes);

        var result = '';
        if (hours < 10) {
          result += '0';
        }
        result += hours + ':';
        if (minutes < 10) {
          result += '0';
        }
        result += minutes;
        return result;
      }
      return '-';
    };
  })
  .name;
