'use strict';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.netflix.tableau.states', [
    require('../../core/navigation/states.provider'),
    require('./application/appTableau.controller'),
    require('./summary/summaryTableau.controller')
  ])
  .config(function(statesProvider) {
    var appTableau = {
      name: 'analytics',
      url: '/analytics',
      reloadOnSearch: false,
      views: {
        'insight': {
          templateUrl: require('./application/appTableau.html'),
          controller: 'AppTableauCtrl as ctrl',
        }
      },
      data: {
        pageTitleSection: {
          title: 'Analytics'
        }
      },
    };

    var summaryTableau = {
      name: 'analytics',
      url: '/analytics',
      reloadOnSearch: false,
      views: {
        'main@': {
          templateUrl: require('./summary/summaryTableau.html'),
          controller: 'SummaryTableauCtrl as ctrl',
        }
      },
      data: {
        pageTitleSection: {
          title: 'Analytics'
        }
      },
    };

    statesProvider.addStateConfig({ parent: 'application', state: appTableau });
    statesProvider.addStateConfig({ parent: 'home', state: summaryTableau });
  });
