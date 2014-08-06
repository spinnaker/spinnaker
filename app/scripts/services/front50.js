'use strict';

module.exports = function(settings, Restangular) {
  return Restangular.withConfig(function(RestangularConfigurer) {
    RestangularConfigurer.setBaseUrl(settings.front50Url);
  });
};
