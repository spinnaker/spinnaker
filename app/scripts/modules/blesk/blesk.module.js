'use strict';

let angular = require('angular');

// TODO: Move to external plugins
module.exports = angular.module('spinnaker.blesk', [
  require('../../settings/settings.js'),
])
  .factory('blesk', function() {
    function initialize() {
      if (angular.element('.spinnaker-header').length && !angular.element('#blesk').length) {
        angular.element('.spinnaker-header')
          .after('<div id="blesk" class="container" data-appid="spinnaker" style="flex: 0 0 auto; padding: 0px"></div>');
        angular.element('body')
          .append('<script async src="https://blesk.prod.netflix.net/static/js/blesk.js"></script>');
      }
    }

    return {
      initialize: initialize,
    };
  })
  .run(function(settings, blesk) {
    if (settings.feature && settings.feature.blesk) {
      blesk.initialize();
    }
  }).name;
