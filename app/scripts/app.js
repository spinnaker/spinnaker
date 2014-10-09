'use strict';

/**
 * @ngdoc overview
 * @name deckApp
 * @description
 * # deckApp
 *
 * Main module of the application.
 */

require('jquery');
var angular = require('angular');

require('angular-animate');
require('angular-bootstrap');
require('angular-spinner');
require('angular-ui-router');
require('angular-ui-select2');
require('restangular');
require('./modules/aws');
require('./modules/gce');

angular.module('deckApp', [
    'ngAnimate',
    'ui.router',
    'ui.bootstrap',
    'ui.select2',
    'restangular',
    'angularSpinner',
    'deckApp.templates',
    'deckApp.aws',
    'deckApp.gce'
  ])
  .run(function($state, $rootScope, $log, $exceptionHandler, cacheInitializer, $modalStack) {
    // This can go away when the next version of ui-router is available (0.2.11+)
    // for now, it's needed because ui-sref-active does not work on parent states
    // and we have to use ng-class. It's gross.
    //
    cacheInitializer.initialize();
    $rootScope.subscribeTo = function(observable) {
      this.subscribed = {
        data: undefined
      };

      observable.subscribe(function(data) {
        this.subscribed.data = data;
      }.bind(this), function(err) {
        $exceptionHandler(err, 'Failed to load data into the view.');
      }.bind(this));
    };

    $rootScope.$state = $state;
    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      $modalStack.dismissAll();
      $log.debug({
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
      $rootScope.routing = true;
    });

    $rootScope.$on('$stateChangeError', function(event, toState, toParams, fromState, fromParams, error) {
      $log.debug({
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams,
        error: error
      });
      $state.go('home.404');
      $rootScope.routing = false;
    });

    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState, fromParams) {
      $log.debug({
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
      $rootScope.routing = false;
    });
  });
