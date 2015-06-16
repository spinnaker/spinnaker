'use strict';

angular.module('spinnaker.notification.types.sms', [])
  .config(function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'SMS',
      key: 'sms',
      addressTemplateUrl: 'scripts/modules/notifications/types/sms/additionalFields.html',
    });
  });
