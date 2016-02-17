'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.kubernetes', [
  require('../../../core/account/account.module.js'),
  require('./configuration.service.js'),
  require('./CommandBuilder.js'),
  require('./wizard/BasicSettings.controller.js'),
  require('./wizard/Clone.controller.js'),
]);
