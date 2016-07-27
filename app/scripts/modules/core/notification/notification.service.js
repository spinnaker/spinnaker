'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.notification.service', [
  require('../api/api.service')
])
  .factory('notificationService', function (settings, API) {

    function getNotificationsForApplication(applicationName) {
      return API.one('notifications').one('application', applicationName).get();
    }

    function saveNotificationsForApplication(applicationName, notifications) {
      return API.one('notifications').one('application', applicationName).data(notifications).post();
    }

    return {
      getNotificationsForApplication: getNotificationsForApplication,
      saveNotificationsForApplication: saveNotificationsForApplication
    };

  });
