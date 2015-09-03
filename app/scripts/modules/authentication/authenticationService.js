'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.authentication.service', [
  require('exports?"ui.bootstrap"!angular-bootstrap'),
])
  .factory('authenticationService', function () {
    var user = {
      name: '[anonymous]',
      authenticated: false
    };

    var onAuthenticationEvents = [];

    function setAuthenticatedUser(authenticatedUser) {
      if (authenticatedUser) {
        user.name = authenticatedUser;
        user.authenticated = true;
      }
      onAuthenticationEvents.forEach(function(event) {
        event();
      });
    }

    function getAuthenticatedUser() {
      return user;
    }

    function onAuthentication(event) {
      onAuthenticationEvents.push(event);
    }

    return {
      setAuthenticatedUser: setAuthenticatedUser,
      getAuthenticatedUser: getAuthenticatedUser,
      onAuthentication: onAuthentication,
    };
  })
  .name;
