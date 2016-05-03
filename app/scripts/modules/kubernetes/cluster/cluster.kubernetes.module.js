'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.cluster.kubernetes', [
  require('./configure/CommandBuilder.js'),
]);
