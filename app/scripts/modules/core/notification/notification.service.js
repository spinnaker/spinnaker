'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.core.notification.service', [
  require('exports?"restangular"!imports?_=lodash!restangular'),
])
  .factory('notificationService', function (settings, Restangular) {

    function getNotificationsForApplication(applicationName){
      return Restangular.one('notifications/application', applicationName).get();
    }

    function saveNotificationsForApplication(applicationName, notifications){
      return Restangular.all('notifications/application/' + applicationName).post(notifications);
    }

    return {
      getNotificationsForApplication: getNotificationsForApplication,
      saveNotificationsForApplication: saveNotificationsForApplication
    };

  });
