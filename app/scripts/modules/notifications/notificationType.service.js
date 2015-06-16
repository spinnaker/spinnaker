'use strict';


angular.module('spinnaker.notifications.service', [
  'spinnaker.notifications.config',
])
  .factory('notificationTypeService', function (notificationTypeConfig, _) {

    function listNotificationTypes() {
      return notificationTypeConfig.listNotificationTypes();
    }

    function getNotificationType(key) {
      return  _.find(notificationTypeConfig.listNotificationTypes(), { key: key });
    }

    return {
      listNotificationTypes: listNotificationTypes,
      getNotificationType: getNotificationType
    };

  });
