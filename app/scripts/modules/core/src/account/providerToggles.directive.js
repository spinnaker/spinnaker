'use strict';

const angular = require('angular');
import { AccountService } from 'core/account/AccountService';

module.exports = angular
  .module('spinnaker.core.account.providerToggle.directive', [])
  .directive('ifMultipleProviders', function() {
    return {
      restrict: 'A',
      link: function(scope, elem) {
        AccountService.listProviders().then(function(providers) {
          if (providers && providers.length && providers.length > 1) {
            elem.show();
          } else {
            elem.hide();
          }
        });
      },
    };
  })
  .directive('ifSingleProvider', function() {
    return {
      restrict: 'A',
      link: function(scope, elem) {
        AccountService.listProviders().then(function(providers) {
          if (!providers || !providers.length || providers.length === 1) {
            elem.show();
          } else {
            elem.hide();
          }
        });
      },
    };
  });
