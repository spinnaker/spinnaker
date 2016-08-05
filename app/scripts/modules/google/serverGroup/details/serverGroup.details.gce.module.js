'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.gce', [
  require('./serverGroupDetails.gce.controller.js'),
  require('../../../core/filter/percent.filter.js'),
]);
