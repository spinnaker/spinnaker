'use strict';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.notifications', [
  require('./notificationTypeConfig.provider'),
  require('./selector/notificationSelector.directive'),
  require('./notificationList.directive'),
  require('./notificationType.service'),
  require('./modal/editNotification.controller.modal'),
  require('./notification.service'),
  require('./notification.details.filter'),
  require('./types/email/email.notification.type.module'),
  require('./types/hipchat/hipchat.notification.type.module'),
  require('./types/slack/slack.notification.type.module'),
  require('./types/sms/sms.notification.type.module'),
]);
