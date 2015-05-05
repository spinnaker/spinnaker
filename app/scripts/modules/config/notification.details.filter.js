'use strict';

angular.module('deckApp.config.notification.details.filter', [])
  .filter('notificationWhen', function() {
    return function(input) {
      input = input.replace('.', ' ').replace('pipeline', 'A pipeline is');

      if(input.indexOf('failed')>-1){
        input = input.replace('pipeline is', 'pipeline has');
      }

      return input;
    };
  }).filter('notificationType', function() {
    return function(input) {
      return input.charAt(0).toUpperCase() + input.slice(1);
    };
  });
