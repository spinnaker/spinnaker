'use strict';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.notification.details.filter', [])
  .filter('notificationWhen', function() {
    return function(input, level, stageType) {
      if (stageType === 'manualJudgment') {
        let filteredInput = 'This stage ';

        if (input === 'manualJudgment') {
          filteredInput += 'is awaiting judgment';
        } else {
          filteredInput += 'was judged to ' + input.slice(14).toLowerCase();
        }

        return filteredInput;
      } else {
        input = input
          .replace('.', ' ')
          .replace('pipeline', (level === 'application' ? 'Any ' : 'This ') + 'pipeline is');
        input = input.replace('.', ' ').replace('stage', 'This stage is ');

        if (input.includes('failed')) {
          input = input.replace('pipeline is', 'pipeline has');
          input = input.replace('stage is', 'stage has');
        }

        return input;
      }
    };
  })
  .filter('notificationType', function() {
    return function(input) {
      return input.charAt(0).toUpperCase() + input.slice(1);
    };
  })
  .filter('notificationDetails', function() {
    return function(input) {
      if (input.type === 'pubsub') {
        return 'Publisher Name: ' + input.publisherName;
      } else if (input.type !== 'email') {
        return input.address;
      } else {
        let addresses = [];
        if (input.address) {
          addresses.push(input.address);
        }
        if (input.cc) {
          addresses.push('cc:' + input.cc);
        }
        return _.join(addresses, ', ');
      }
    };
  });
