'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.cache', [
    require('./collapsibleSectionStateCache.js'),
  ]);
