'use strict';
const _ = require('lodash');

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.utils.lodash', [])
  .constant('_', _ );
