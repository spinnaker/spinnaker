'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.serverGroup.details', [
  require('./details.controller.js'),
  require('./resize/resize.controller.js'),
]);
