'use strict';

/**
 * @ngdoc overview
 * @name deckApp
 * @description
 * # deckApp
 *
 * Main module of the application.
 */
angular
  .module('deckApp', [
    'ui.router'
  ])
  .config(function ($stateProvider, $urlRouterProvider, $logProvider) {
    $logProvider.debugEnabled(true);
    $urlRouterProvider.otherwise('/');

    $stateProvider
      .state('home', {
        url: '/',
        views: {
          'main': {
            templateUrl: 'views/main.html',
            controller: 'MainCtrl'
          }
        }
      })

      .state('applications', {
        url: '/applications',
        views: {
          'main': {
            templateUrl: 'views/application.html',
            controller: 'ApplicationCtrl'
          }
        },
        resolve: {
          application: function() {
            return {};
          }
        }
      })
  });
