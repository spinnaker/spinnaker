'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.common', [
  require('./serverGroupConfiguration.service.js')
])
.name;
