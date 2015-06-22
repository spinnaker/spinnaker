'use strict';
const _ = require('lodash');

let angular = require('angular');

module.exports = angular.module('spinnaker.utils.lodash', [])
  .constant('_', _ )
  .name;
