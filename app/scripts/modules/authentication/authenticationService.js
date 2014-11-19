'use strict';

angular.module('deckApp.authentication')
  .factory('authenticationService', function ($http, settings, $location, $window, $modal, redirectService) {
    var user = { name: null };

    function setAuthenticatedUser(authenticatedUser) {
      user.name = authenticatedUser;
    }

    function getAuthenticatedUser() {
      return user;
    }

    function authenticateUser() {
      var modal = $modal.open({
        templateUrl: 'scripts/modules/authentication/authenticating.html'
      });
      $http.get(settings.gateUrl + '/auth/info')
        .success(function (data) {
          if (data.email) {
            setAuthenticatedUser(data.email);
          }
          modal.dismiss();
        })
        .error(function (data, status, headers) {
          var redirect = headers('X-AUTH-REDIRECT-URL');
          if (status === 401 && redirect) {
            redirectService.redirect(settings.gateUrl + redirect + '?callback=' + $window.location.origin + '&path=' + $location.path());
          }
          modal.dismiss();
        });
    }

    return {
      setAuthenticatedUser: setAuthenticatedUser,
      getAuthenticatedUser: getAuthenticatedUser,
      authenticateUser: authenticateUser
    };
  })
  .factory('redirectService', function($window) {
    // this exists so we can spy on the location without actually changing the window location in tests
    return {
      redirect: function(url) {
        $window.location.href = url;
      }
    }
  });
