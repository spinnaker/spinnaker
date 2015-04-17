'use strict';

/**
 * @ngdoc overview
 * @name deckApp
 * @description
 * # deckApp
 *
 * Main module of the application.
 */

window.spinnakerPlugins = window.spinnakerPlugins || [];

angular.module('deckApp', [
    'angulartics',
    'angulartics.google.analytics',
    'ngAnimate',
    'ngSanitize',
    'ui.router',
    'ui.bootstrap',
    'ui.select',
    'restangular',
    'angularSpinner',
    'angular.filter',
    'deckApp.states',
    'deckApp.delivery.states',

    'deckApp.insight',
    'deckApp.application',
    'deckApp.feedback',

    'deckApp.utils.stickyHeader',

    'deckApp.aws.loadBalancer.transformer.service',
    'deckApp.gce.loadBalancer.transformer.service',

    'deckApp.templates',
    'deckApp.aws',
    'deckApp.gce',
    'deckApp.subnet',
    'deckApp.utils',
    'deckApp.caches',
    'deckApp.naming',
    'deckApp.delegation',
    'deckApp.healthCounts.directive',
    'deckApp.settings',
    'deckApp.scheduler',
    'deckApp.urlBuilder',
    'deckApp.cluster',
    'deckApp.modalWizard',
    'deckApp.confirmationModal.service',
    'deckApp.ajaxError.interceptor',
    'deckApp.deploymentStrategy',
    'deckApp.deploymentStrategy.redblack',
    'deckApp.deploymentStrategy.none',
    'deckApp.deploymentStrategy.highlander',
    'deckApp.securityGroup',
    'deckApp.serverGroup',
    'deckApp.instance',
    'deckApp.pageTitle',
    'deckApp.securityGroup',
    'deckApp.serverGroup',
    'deckApp.instance',
    'deckApp.help',
    'deckApp.delivery',
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
    'deckApp.pipelines.stage.enableAsg',
    'deckApp.pipelines.stage.modifyScalingProcess',
    'deckApp.pipelines.stage.destroyAsg',
    'deckApp.pipelines.stage.disableAsg',
    'deckApp.pipelines.stage.executionWindows',
    'deckApp.pipelines.stage.findAmi',
    'deckApp.authentication',
    'deckApp.search',
    'deckApp.notifications',
    'deckApp.tasks',
    'deckApp.validation',
    'deckApp.loadBalancer',
    'deckApp.vpc',
    'deckApp.keyPairs',
    'deckApp.config',
    'deckApp.gist.directive',
    'deckApp.whatsNew.directive',
    'deckApp.help.directive',
    'deckApp.networking',
].concat(window.spinnakerPlugins))
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
      $log.debug(event.name, {
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
      pageTitleService.handleRoutingStart();
    });

    $rootScope.$on('$stateChangeError', function(event, toState, toParams, fromState, fromParams, error) {
      $log.debug(event.name, {
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
      $log.debug(event.name, {
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
      pageTitleService.handleRoutingSuccess(toState.data);
    });
  })
  .config(function($animateProvider) {
    $animateProvider.classNameFilter(/animated/);

  });
