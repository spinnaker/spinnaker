import { IExceptionHandlerService, module } from 'angular';
import IInjectorService = angular.auto.IInjectorService;

import { AUTHENTICATION_SERVICE, AuthenticationService, SETTINGS } from '@spinnaker/core';

export const EXCEPTION_HANDLER = 'spinnaker.netflix.exception.handler';
module(EXCEPTION_HANDLER, [AUTHENTICATION_SERVICE])
  .config(($provide: angular.auto.IProvideService) => {

    $provide.decorator('$exceptionHandler', ($injector: IInjectorService,
                                             $delegate: IExceptionHandlerService,
                                             authenticationService: AuthenticationService) => {

      const currentVersion = require('../../../../../version.json');
      return (exception: Error, cause: string) => {

        $delegate(exception, cause);
        const $http = $injector.get('$http'); // using injector access to avoid a circular dependency
        if (SETTINGS.alert) {

          let message: string = exception.message;
          if (!message) {
            try {
              message = JSON.stringify(exception);
            } catch (e) {
              message = '[No message available - could not convert exception to JSON string]';
            }
          }

          const payload = {
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
        }
      };
    });
  });
