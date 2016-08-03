'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.authentication.initializer.service', [
  require('../config/settings.js'),
  require('../widgets/notifier/notifier.service.js'),
  require('./authentication.service.js'),
  require('../utils/rx'),
])
  .factory('authenticationInitializer', function ($http, $rootScope, rx, notifierService, redirectService, authenticationService, settings, $location) {

    let userLoggedOut = false;
    let visibilityWatch = null;

    function reauthenticateUser() {
      if (userLoggedOut) {
        return;
      }
      $http.get(settings.authEndpoint)
        .success(function (data) {
          if (data.username) {
            authenticationService.setAuthenticatedUser(data.username);
          } else {
            loginNotification();
          }
        })
        .error(loginNotification);
    }

    function authenticateUser() {
      $rootScope.authenticating = true;
      $http.get(settings.authEndpoint)
        .success(function (data) {
          if (data.username) {
            authenticationService.setAuthenticatedUser(data.username);
            $rootScope.authenticating = false;
          } else {
            loginRedirect();
          }
        })
        .error(loginRedirect);
    }

    function checkForReauthentication() {
      $http.get(settings.authEndpoint)
        .success(function (data) {
          if (data.username) {
            authenticationService.setAuthenticatedUser(data.username);
            notifierService.clear('loggedOut');
            visibilityWatch.dispose();
          }
        });
    }

    function loginNotification() {
      authenticationService.authenticationExpired();
      userLoggedOut = true;
      notifierService.publish({
        position: 'top',
        key: 'loggedOut',
        body: `You have been logged out. <a role="button" class="action" onclick="document.location.reload()">Log in</a>`
      });
      visibilityWatch = rx.Observable.fromEvent(document, 'visibilitychange').subscribe(() => {
        if (document.visibilityState === 'visible') {
          checkForReauthentication();
        }
      });
    }

    /**
     * This function hits a protected resource endpoint specifically meant for Deck's
     * login flow.
     */
    function loginRedirect() {
      var callback = encodeURIComponent($location.absUrl());
      redirectService.redirect(settings.gateUrl + '/auth/redirect?to=' + callback);
    }

    return {
      authenticateUser: authenticateUser,
      reauthenticateUser: reauthenticateUser
    };
  })
  .factory('redirectService', function($window) {
    // this exists so we can spy on the location without actually changing the window location in tests
    return {
      redirect: function(url) {
        $window.location.href = url;
      }
    };
  });
