'use strict';

angular.module('deckApp')
  .factory('pond', function(settings, Restangular) {
    return Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.pondUrl);
    });
  });
