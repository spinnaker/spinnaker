'use strict';

import {AUTHENTICATION_SERVICE} from 'core/authentication/authentication.service';
import {SETTINGS} from 'core/config/settings';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.alert.handler', [
    AUTHENTICATION_SERVICE
  ])
  .config(function ($provide) {
    $provide.decorator('$exceptionHandler', function($delegate, authenticationService, $injector) {
      let currentVersion = require('../../../../../version.json');

      return function(exception, cause) {
        $delegate(exception, cause);
        // using injector access to avoid a circular dependency
        let $http = $injector.get('$http');
        if (!SETTINGS.alert) {
          return;
        }
        let message = exception.message;
        if (!message) {
          try {
            message = JSON.stringify(exception);
          } catch (e) {
            message = '[No message available - could not convert exception to JSON string]';
          }
        }
        let payload = {
          alertName: 'Spinnaker',
          details: {
            url: location.href,
            user: authenticationService.getAuthenticatedUser().name,
            version: currentVersion.version,
          },
          exception: {
            classes: [exception.name || '[no name on exception]'],
            messages: [message],
            stackTraces: [exception.stack || '[no stacktrace available]'],
            callerClass: 'Spinnaker',
            callerMethod: '[see stack trace]',
          },
          actions: [
            {
              action: 'email',
              suppressTimeSecs: SETTINGS.alert.throttleInSeconds,
              to: SETTINGS.alert.recipients,
              subject: SETTINGS.alert.subject || '[Spinnaker] Error in Deck',
              htmlTemplate: SETTINGS.alert.template || 'spinnaker_deck_error',
              incidentKey: exception.message,
            }
          ],
        };

        $http.post(SETTINGS.alert.url, payload);
      };
    });
  });
