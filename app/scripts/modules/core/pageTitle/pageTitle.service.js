'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.pageTitle.service',
    [require('angular-ui-router')]
  )
  .factory('pageTitleService', function($rootScope, $stateParams) {

    function handleRoutingStart() {
      $rootScope.routing = true;
      $rootScope.pageTitle = 'Spinnaker: Loading...';
    }

    function handleRoutingError() {
      $rootScope.routing = false;
      $rootScope.pageTitle = 'Spinnaker: Error';
    }

    function handleRoutingSuccess(config) {
      var parts = configurePageTitle(config);
      var title = parts.main || 'Spinnaker';
      if (parts.section) {
        title += ' · ' + parts.section;
      }
      if (parts.details) {
        title += ' · ' + parts.details;
      }
      $rootScope.routing = false;
      $rootScope.pageTitle = title;
    }

    function resolveStateParams(config) {
      if (!config) {
        return null;
      }
      var result = config.title;
      if (config.nameParam) {
        result += ': ' + $stateParams[config.nameParam];
      }
      if (config.accountParam || config.regionParam) {
        result += ' (';
        if (config.accountParam && config.regionParam) {
          result += $stateParams[config.accountParam] + ':' + $stateParams[config.regionParam];
        } else {
          result += $stateParams[config.accountParam] || $stateParams[config.regionParam];
        }
        result += ')';
      }
      return result;
    }

    function configureSection(sectionConfig) {
      return resolveStateParams(sectionConfig);
    }

    function configureDetails(detailsConfig) {
      return resolveStateParams(detailsConfig);
    }

    function configureMain(mainConfig) {
      var main = null;
      if (!mainConfig) {
        return main;
      }
      if (mainConfig.field) {
        main = $stateParams[mainConfig.field];
      }
      if (mainConfig.label) {
        main = mainConfig.label;
      }
      return main;
    }

    function configurePageTitle(data={}) {
      return {
        main: configureMain(data.pageTitleMain),
        section: configureSection(data.pageTitleSection),
        details: configureDetails(data.pageTitleDetails)
      };
    }

    return {
      handleRoutingStart: handleRoutingStart,
      handleRoutingSuccess: handleRoutingSuccess,
      handleRoutingError: handleRoutingError
    };

  })
  .run(function($rootScope, pageTitleService) {
    $rootScope.$on('$stateChangeStart', function() {
      pageTitleService.handleRoutingStart();
    });

    $rootScope.$on('$stateChangeError', function() {
      pageTitleService.handleRoutingError();
    });

    $rootScope.$on('$stateChangeSuccess', function(event, toState) {
      pageTitleService.handleRoutingSuccess(toState.data);
    });
  });
