'use strict';

let angular = require('angular');
import {ACCOUNT_SERVICE} from 'core/account/account.service';

module.exports = angular
  .module('spinnaker.core.account.providerToggle.directive', [ACCOUNT_SERVICE])
  .directive('ifMultipleProviders', function(accountService) {
    return {
      restrict: 'A',
      link: function(scope, elem) {
        accountService.listProviders().then(function(providers) {
          if (providers && providers.length && providers.length > 1) {
            elem.show();
          } else {
            elem.hide();
          }
        });
      }
    };
  })
  .directive('ifSingleProvider', function(accountService) {
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
  });
