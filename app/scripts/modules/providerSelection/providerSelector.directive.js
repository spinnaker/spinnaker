'use strict';

angular.module('deckApp.providerSelection.directive', [
  'deckApp.account.service',
])
  .directive('providerSelector', function(accountService) {
    return {
      restrict: 'E',
      scope: {
        component: '=',
        field: '@',
        readOnly: '=',
      },
      templateUrl: 'scripts/modules/providerSelection/providerSelector.html',
      link: function(scope) {
        scope.initialized = false;
        accountService.listProviders().then(function(providers) {
          scope.initialized = true;
          if (!providers.length) {
            scope.component[scope.field] = 'aws';
          }
          if (providers.length === 1) {
            scope.component[scope.field] = providers[0];
          }
          if (providers.length > 1) {
            scope.providers = providers;
            scope.showSelector = true;
          }
        });
      },
    };
  });
