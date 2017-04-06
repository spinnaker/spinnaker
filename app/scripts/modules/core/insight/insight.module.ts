'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.insight', [
    require('./insight.controller.js')
  ]);
