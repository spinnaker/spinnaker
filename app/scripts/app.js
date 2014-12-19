'use strict';

/**
 * @ngdoc overview
 * @name deckApp
 * @description
 * # deckApp
 *
 * Main module of the application.
 */


angular.module('deckApp', [
    'ngAnimate',
    'ngSanitize',
    'ui.router',
    'ui.bootstrap',
    'ui.select',
    'restangular',
    'angularSpinner',
    'angular.filter',
    'deckApp.templates',
    'deckApp.aws',
    'deckApp.gce',
    'deckApp.subnet',
    'deckApp.utils',
    'deckApp.caches',
    'deckApp.settings',
    'deckApp.scheduler',
    'deckApp.urlBuilder',
    'deckApp.cluster',
    'deckApp.deploymentStrategy',
    'deckApp.deploymentStrategy.redblack',
    'deckApp.deploymentStrategy.none',
    'deckApp.deploymentStrategy.highlander',
    'deckApp.applications',
    'deckApp.securityGroup',
    'deckApp.serverGroup',
    'deckApp.instance',
    'deckApp.pageTitle',
    'deckApp.securityGroup',
    'deckApp.serverGroup',
    'deckApp.instance',
    'deckApp.pipelines',
    'deckApp.pipelines.trigger',
    'deckApp.pipelines.trigger.jenkins',
    'deckApp.pipelines.stage',
    'deckApp.pipelines.stage.bake',
    'deckApp.pipelines.stage.deploy',
    'deckApp.pipelines.stage.script',
    'deckApp.pipelines.stage.wait',
    'deckApp.pipelines.stage.jenkins',
    'deckApp.pipelines.stage.resizeAsg',
    'deckApp.authentication',
    'deckApp.delivery',
    'deckApp.search',
    'deckApp.notifications',
    'deckApp.tasks',
    'deckApp.validation',
    'deckApp.loadBalancer',
    'deckApp.vpc',
    'deckApp.keyPairs',
    'deckApp.config'
  ])
  .run(function($state, $rootScope, $log, $exceptionHandler, cacheInitializer, $modalStack, pageTitleService) {
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
      pageTitleService.handleRoutingStart();
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
      pageTitleService.handleRoutingError();
    });

    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState, fromParams) {
      $log.debug({
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
      pageTitleService.handleRoutingSuccess(toState.data);
    });
  });
