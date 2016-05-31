'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.authentication.initializer.service', [
  require('../config/settings.js'),
  require('../widgets/notifier/notifier.service.js'),
  require('./authentication.service.js'),
])
  .factory('authenticationInitializer', function ($http, $rootScope, notifierService, redirectService, authenticationService, settings, $location) {

    function reauthenticateUser() {
      $http.get(settings.authEndpoint)
        .success(function (data) {
          if (data.email) {
            authenticationService.setAuthenticatedUser(data.email);
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
          if (data.email) {
            authenticationService.setAuthenticatedUser(data.email);
            $rootScope.authenticating = false;
          } else {
            loginRedirect();
          }
        })
        .error(loginRedirect);
    }

    function loginNotification() {
      notifierService.publish(`You have been logged out. <a role="button" class="action" onclick="document.location.reload()">Log in</button>`);
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
