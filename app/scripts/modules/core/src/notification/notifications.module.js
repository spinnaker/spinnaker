'use strict';

import { SPINNAKER_CORE_NOTIFICATION_TYPES_GITHUB_STATUS } from './types/githubstatus/githubstatus.notification.type.module';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.notifications', [
  require('./notificationTypeConfig.provider').name,
  require('./selector/notificationSelector.directive').name,
  require('./notificationList.directive').name,
  require('./notificationType.service').name,
  require('./modal/editNotification.controller.modal').name,
  require('./notification.details.filter').name,
  require('./types/bearychat/bearychat.notification.type.module').name,
  require('./types/email/email.notification.type.module').name,
  SPINNAKER_CORE_NOTIFICATION_TYPES_GITHUB_STATUS,
  require('./types/googlechat/googlechat.notification.type.module').name,
  require('./types/pubsub/pubsub.notification.type.module').name,
  require('./types/slack/slack.notification.type.module').name,
  require('./types/sms/sms.notification.type.module').name,
]);
