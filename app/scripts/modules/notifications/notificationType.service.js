'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.notifications.service', [
  require('./notificationTypeConfig.provider.js'),
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

  }).name;
