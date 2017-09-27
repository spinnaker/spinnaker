'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.serverGroup.details', [
  require('./details.controller.js').name,
  require('./resize/resize.controller.js').name,
]);
