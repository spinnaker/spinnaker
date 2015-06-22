'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.notification.types.email', [])
  .config(function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'Email',
      key: 'email',
      addressTemplateUrl: 'scripts/modules/notifications/types/email/additionalFields.html',
    });
  });
