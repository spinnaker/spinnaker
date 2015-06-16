'use strict';

angular.module('spinnaker.notification.types.hipchat', [])
  .config(function(notificationTypeConfigProvider) {
    notificationTypeConfigProvider.registerNotificationType({
      label: 'HipChat',
      key: 'hipchat',
      addressTemplateUrl: 'scripts/modules/notifications/types/hipchat/additionalFields.html',
    });
  });
