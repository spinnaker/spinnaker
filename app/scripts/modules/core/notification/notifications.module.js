'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.notifications', [
  require('./notificationTypeConfig.provider.js'),
  require('./selector/notificationSelector.directive.js'),
  require('./notificationList.directive.js'),
  require('./notificationType.service.js'),
  require('./modal/editNotification.controller.modal.js'),
  require('../confirmationModal/confirmationModal.service.js'),
  require('./notification.service.js'),
  require('./notification.details.filter.js')
]);
