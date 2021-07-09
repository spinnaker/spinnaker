'use strict';

import { module } from 'angular';
import { AccountService } from './AccountService';

export const CORE_ACCOUNT_PROVIDERTOGGLES_DIRECTIVE = 'spinnaker.core.account.providerToggle.directive';
export const name = CORE_ACCOUNT_PROVIDERTOGGLES_DIRECTIVE; // for backwards compatibility
module(CORE_ACCOUNT_PROVIDERTOGGLES_DIRECTIVE, [])
  .directive('ifMultipleProviders', function () {
    return {
      restrict: 'A',
      link: function (scope, elem) {
        AccountService.listProviders().then(function (providers) {
          if (providers && providers.length && providers.length > 1) {
            elem.show();
          } else {
            elem.hide();
          }
        });
      },
    };
  })
  .directive('ifSingleProvider', function () {
    return {
      restrict: 'A',
      link: function (scope, elem) {
        AccountService.listProviders().then(function (providers) {
          if (!providers || !providers.length || providers.length === 1) {
            elem.show();
          } else {
            elem.hide();
          }
        });
      },
    };
  });
