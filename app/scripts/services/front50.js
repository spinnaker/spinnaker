'use strict';

require('../app');
var angular = require('angular');

angular.module('deckApp')
  .factory('front50', function(settings, Restangular) {
    return Restangular.withConfig(function(RestangularConfigurer) {
      RestangularConfigurer.setDefaultHttpFields({ cache: true });
      RestangularConfigurer.setBaseUrl(settings.front50Url);
    });
  });
