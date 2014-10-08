'use strict';

angular.module('deckApp')
  .factory('cacheInitializer', function(accountService, instanceTypeService, settings) {
    return {
      initialize: function() {
        if (!settings.cacheInitializerDisabled) {
          accountService.getRegionsKeyedByAccount();
          instanceTypeService.getAllTypesByRegion();
        }
      }
    };
  });
