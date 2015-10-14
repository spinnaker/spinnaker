'use strict';

let angular = require('angular');
require('./acaStrategy.less');

module.exports = angular
  .module('spinnaker.fastProperties.aca.strategy', [
    require('./aca.config.js'),
    require('./aca.controller'),
  ])
  .name;
