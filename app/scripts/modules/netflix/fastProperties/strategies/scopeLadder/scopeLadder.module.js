'use strict';
let angular = require('angular');

module.exports = angular
  .module('spinnaker.fastProptery.ladder.strategy', [
    require('./scopeLadder.config.js'),
    require('./scopeLadder.controller.js'),
  ]);


