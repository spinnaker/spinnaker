'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.authentication.initializer.service', [
  require('../config/settings.js'),
  require('./authentication.service.js'),
])
  .factory('authenticationInitializer', function ($http, $rootScope, redirectService, authenticationService, settings, $location) {

    function authenticateUser() {
      $rootScope.authenticating = true;
      $http.get(settings.authEndpoint)
        .success(function (data) {
          if (data.email) {
            authenticationService.setAuthenticatedUser(data.email);
          }
          $rootScope.authenticating = false;
        })
        .error(function (data, status, headers) {
          var redirect = headers('X-AUTH-REDIRECT-URL');
          if (status === 401 && redirect) {
            var callback = encodeURIComponent($location.absUrl());
            redirectService.redirect(settings.gateUrl + redirect + '?callback=' + callback);
          } else {
            $rootScope.authenticating = false;
          }
        });
    }

    return {
      authenticateUser: authenticateUser
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
