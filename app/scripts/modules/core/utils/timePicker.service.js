'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.timePicker.service', [
  require('./lodash.js'),
])
  .factory('timePickerService', function(_) {

    function getHours() {
      var hours = [];
      _.range(0, 10).forEach(function(hour) {
        hours.push({label: '0' + hour, key: hour});
      });
      _.range(10, 24).forEach(function(hour) {
        hours.push({label: hour, key: hour});
      });
      return hours;
    }

    function getMinutes() {
      var minutes = [];
      _.range(0, 10).forEach(function(minute) {
        minutes.push({label: '0' + minute, key: minute});
      });
      _.range(10, 60).forEach(function(minute) {
        minutes.push({label: minute, key: minute});
      });
      return minutes;
    }

    return {
      getHours: getHours,
      getMinutes: getMinutes,
    };

  });
