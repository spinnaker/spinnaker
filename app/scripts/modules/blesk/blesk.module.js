'use strict';

// TODO: Move to external plugins
angular.module('deckApp.blesk', ['deckApp.settings'])
  .factory('blesk', function() {
    function initialize() {
      angular.element('.container-main')
        .prepend('<div id="blesk" data-appid="spinnaker"></div>');
      angular.element('body')
        .append('<script async src="https://blesk.prod.netflix.net/static/js/blesk.js"></script>');
    }

    return {
      initialize: initialize,
    };
  })
  .run(function(settings, blesk) {
    if (settings.feature && settings.feature.blesk) {
      blesk.initialize();
    }
  });
