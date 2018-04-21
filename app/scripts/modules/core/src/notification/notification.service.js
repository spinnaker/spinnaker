'use strict';

import { API } from 'core/api/ApiService';

const angular = require('angular');

module.exports = angular.module('spinnaker.core.notification.service', []).factory('notificationService', function() {
  function getNotificationsForApplication(applicationName) {
    return API.one('notifications')
      .one('application', applicationName)
      .get();
  }

  function saveNotificationsForApplication(applicationName, notifications) {
    return API.one('notifications')
      .one('application', applicationName)
      .data(notifications)
      .post();
  }

  return {
    getNotificationsForApplication: getNotificationsForApplication,
    saveNotificationsForApplication: saveNotificationsForApplication,
  };
});
