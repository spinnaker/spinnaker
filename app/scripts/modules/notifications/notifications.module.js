'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.notifications', [
  require('./notificationTypeConfig.provider.js'),
  require('./selector/notificationSelector.directive.js'),
  require('./notificationType.service.js'),
]);
