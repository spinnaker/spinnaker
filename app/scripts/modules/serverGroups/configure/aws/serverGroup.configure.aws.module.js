'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.aws', [
  require('../../../account/account.module.js'),
  require('./wizard/deployInitializer.controller.js'),
  require('../../../caches/infrastructureCaches.js'),
]);
