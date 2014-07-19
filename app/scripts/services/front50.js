'use strict';

angular.module('deckApp')
  .factory('front50', function(settings, Restangular) {
    return Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setBaseUrl(settings.front50Url);
    });
  });
