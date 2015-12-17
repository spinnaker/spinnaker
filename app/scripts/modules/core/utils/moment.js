'use strict';

let moment = require('moment-timezone');
let angular = require('angular');

module.exports = angular.module('spinnaker.core.utils.moment', [])
  .factory('momentService', function() {
    return moment;
  });
