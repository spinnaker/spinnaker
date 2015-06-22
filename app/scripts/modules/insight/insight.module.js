'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.insight', [
    require('./insight.controller.js')
  ]);
