'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.alert.handler', [
    require('../../core/config/settings.js'),
    require('../../core/authentication/authentication.service.js'),
  ])
  .config(function ($provide) {
    $provide.decorator('$exceptionHandler', function($delegate, settings, authenticationService, $) {
      return function(exception, cause) {
        $delegate(exception, cause);
        if (!settings.alert) {
          return;
        }
        let payload = {
          alertName: 'Spinnaker',
          details: {
            url: location.href,
            user: authenticationService.getAuthenticatedUser().name,
          },
          exception: {
            classes: [exception.name],
            messages: [exception.message],
            stackTraces: [exception.stack],
            callerClass: 'Spinnaker',
            callerMethod: '[see stack trace]',
          },
          actions: [
            {
              action: 'email',
              suppressTimeSecs: settings.alert.throttleInSeconds,
              to: settings.alert.recipients,
              subject: settings.alert.subject || '[Spinnaker] Error in Deck',
              htmlTemplate: settings.alert.template || 'spinnaker_deck_error',
              incidentKey: exception.message,
            }
          ],
        };
        if (navigator.sendBeacon) {
          navigator.sendBeacon(settings.alert.url, JSON.stringify(payload));
        } else {
          console.warn('no beacon support :(');
          $.post(settings.alert.url, JSON.stringify(payload));
        }
      };
    });
  }).name;
