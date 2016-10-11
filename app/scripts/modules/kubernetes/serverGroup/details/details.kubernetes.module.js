'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.serverGroup.details.kubernetes', [
  require('core/account/account.module.js'),
  require('./details.controller.js'),
  require('./resize/resize.controller.js'),
  require('./rollback/rollback.controller.js'),
]);
