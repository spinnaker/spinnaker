'use strict';

module.exports = function(accountService) {
  return {
    restrict: 'A',
    link: function(scope, elem) {
      accountService.listProviders().then(function(providers) {
        if (!providers || !providers.length || providers.length === 1) {
          elem.show();
        } else {
          elem.hide();
        }
      });
    }
  };
};
