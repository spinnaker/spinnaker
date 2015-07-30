'use strict';
const _ = require('lodash');
let angular = require('angular');

module.exports = angular.module('spinnaker.utils.lodash', [])
  .factory('_', function() {
    return _;
  })
  .name;
