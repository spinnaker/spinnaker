'use strict';

let angular = require('angular');
require('./acaStrategy.less');

module.exports = angular
  .module('spinnaker.netflix.fastProperties.aca.strategy', [
    require('./aca.config.js'),
    require('./aca.controller'),
  ]);
