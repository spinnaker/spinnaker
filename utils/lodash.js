'use strict';
let _ = require('lodash');
let angular = require('angular');

module.exports = angular.module('spinnaker.utils.lodash', [])
  .factory('_', function() {
    return _;
  });
