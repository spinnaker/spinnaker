'use strict';

angular.module('deckApp.config.notification.details.filter', [])
  .filter('notificationWhen', function() {
    return function(input) {
      input = input.replace('.', ' ').replace('pipeline', 'A pipeline has');

      if(input.indexOf('complete')>-1){
        input = input.replace('pipeline has complete', 'pipeline has completed');
      }

      return input;
    };
  }).filter('notificationType', function() {
    return function(input) {
      return input.charAt(0).toUpperCase() + input.slice(1);
    };
  });
