'use strict';

let angular = require('angular');

require('./bleskOverrides.css');

// TODO: Move to external plugins
module.exports = angular.module('spinnaker.netflix.blesk', [
  require('../../core/config/settings.js'),
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
  .run(function(settings, blesk, $timeout) {
    if (settings.feature && settings.feature.blesk) {
      // putting a delay on initialization so authentication can take place and dom can finish loading.
      $timeout(blesk.initialize, 5000);
    }
  });
