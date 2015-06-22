'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.configure.gce', [
  require('../../../account/account.module.js'),
  require('./wizard/deployInitializer.controller.js'),
]);
