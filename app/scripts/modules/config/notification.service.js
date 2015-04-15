'use strict';


angular.module('deckApp.config.notification.service', [
  'restangular',
  'deckApp.settings'
])
  .factory('notificationService', function (settings, Restangular) {

    function getNotificationsForApplication(applicationName){
      return Restangular.one('notifications/application', applicationName).get();
    }

    return {
      getNotificationsForApplication: getNotificationsForApplication
    };

  });
