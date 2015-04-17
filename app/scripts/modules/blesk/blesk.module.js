'use strict';

// TODO: Move to external plugins
angular.module('deckApp.blesk', [])
  .provider('bleskModule', function() {
    angular.element('body')
      .append('<script async src="https://blesk.prod.netflix.net/static/js/blesk.js"></script>')
      .prepend('<div id="blesk" style="top: -75px; z-index: 2000; position: relative" data-appid="spinnaker"></div>');

    this.$get = function() {
      return {};
    };
  });
