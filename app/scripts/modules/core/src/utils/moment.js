'use strict';

let moment = require('moment-timezone');
const angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.moment', [])
  .factory('momentService', function() {
    return moment;
  });
