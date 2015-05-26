'use strict';

angular.module('spinnaker.authentication.service', [
  'ui.bootstrap',
  'spinnaker.settings',
])
  .factory('authenticationService', function ( $rootScope, $http, $location, $window, $modal, settings, redirectService ) {
    var user = {
      name: '[anonymous]',
      authenticated: false
    };

    function setAuthenticatedUser(authenticatedUser) {
      if (authenticatedUser) {
        user.name = authenticatedUser;
        user.authenticated = true;
      }
    }

    function getAuthenticatedUser() {
      return user;
    }

    function authenticateUser() {
      $rootScope.authenticating = true;
      $http.get(settings.gateUrl + '/auth/info')
        .success(function (data) {
          if (data.email) {
            setAuthenticatedUser(data.email);
          }
          $rootScope.authenticating = false;
        })
        .error(function (data, status, headers) {
          var redirect = headers('X-AUTH-REDIRECT-URL');
          if (status === 401 && redirect) {
            $modal.open({
              templateUrl: 'scripts/modules/authentication/authenticating.html',
              windowClass: 'modal no-animate',
              backdropClass: 'modal-backdrop-no-animate',
              backdrop: 'static',
              keyboard: false
            });
            var callback = encodeURIComponent($location.absUrl());
            redirectService.redirect(settings.gateUrl + redirect + '?callback=' + callback);
          } else {
            $rootScope.authenticating = false;
          }
        });
    }

    return {
      setAuthenticatedUser: setAuthenticatedUser,
      getAuthenticatedUser: getAuthenticatedUser,
      authenticateUser: authenticateUser,
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
