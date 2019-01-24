'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.dcos.serverGroup.details', [
  require('./details.controller').name,
  require('./resize/resize.controller').name,
]);
