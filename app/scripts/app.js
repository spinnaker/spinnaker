'use strict';

/**
 * @ngdoc overview
 * @name spinnaker
 * @description
 * # spinnaker
 *
 * Main module of the application.
 */

angular.module('spinnaker', [
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
    'spinnaker.states',
    'spinnaker.delivery.states',
    'infinite-scroll',

    'spinnaker.insight',
    'spinnaker.application',
    'spinnaker.feedback',

    'spinnaker.utils.stickyHeader',

    'spinnaker.aws.loadBalancer.transformer.service',
    'spinnaker.gce.loadBalancer.transformer.service',

    'spinnaker.templates',
    'spinnaker.aws',
    'spinnaker.gce',
    'spinnaker.subnet',
    'spinnaker.utils',
    'spinnaker.caches',
    'spinnaker.naming',
    'spinnaker.delegation',
    'spinnaker.healthCounts.directive',
    'spinnaker.settings',
    'spinnaker.scheduler',
    'spinnaker.urlBuilder',
    'spinnaker.cluster',
    'spinnaker.modalWizard',
    'spinnaker.confirmationModal.service',
    'spinnaker.ajaxError.interceptor',
    'spinnaker.deploymentStrategy',
    'spinnaker.deploymentStrategy.redblack',
    'spinnaker.deploymentStrategy.none',
    'spinnaker.deploymentStrategy.highlander',
    'spinnaker.deploymentStrategy.rollingPush',
    'spinnaker.securityGroup',
    'spinnaker.serverGroup',
    'spinnaker.instance',
    'spinnaker.pageTitle',
    'spinnaker.securityGroup',
    'spinnaker.serverGroup',
    'spinnaker.instance',
    'spinnaker.help',
    'spinnaker.delivery',
    'spinnaker.pipelines',
    'spinnaker.pipelines.trigger',
    'spinnaker.pipelines.trigger.jenkins',
    'spinnaker.pipelines.trigger.pipeline',
    'spinnaker.pipelines.stage',
    'spinnaker.pipelines.stage.bake',
    'spinnaker.pipelines.stage.canary',
    'spinnaker.pipelines.stage.deploy',
    'spinnaker.pipelines.stage.determineTargetReference',
    'spinnaker.pipelines.stage.script',
    'spinnaker.pipelines.stage.wait',
    'spinnaker.pipelines.stage.jenkins',
    'spinnaker.pipelines.stage.pipeline',
    'spinnaker.pipelines.stage.resizeAsg',
    'spinnaker.pipelines.stage.enableAsg',
    'spinnaker.pipelines.stage.modifyScalingProcess',
    'spinnaker.pipelines.stage.destroyAsg',
    'spinnaker.pipelines.stage.disableAsg',
    'spinnaker.pipelines.stage.executionWindows',
    'spinnaker.pipelines.stage.findAmi',
    'spinnaker.pipelines.stage.quickPatchAsg',
    'spinnaker.pipelines.stage.quickPatchAsg.bulkQuickPatchStage',
    'spinnaker.pipelines.stage.manualJudgment',
    'spinnaker.authentication',
    'spinnaker.search',
    'spinnaker.notifications',
    'spinnaker.notification.types.email',
    'spinnaker.notification.types.hipchat',
    'spinnaker.notification.types.sms',
    'spinnaker.tasks',
    'spinnaker.validation',
    'spinnaker.loadBalancer',
    'spinnaker.vpc',
    'spinnaker.keyPairs',
    'spinnaker.config',
    'spinnaker.gist.directive',
    'spinnaker.whatsNew.directive',
    'spinnaker.help.directive',
    'spinnaker.networking',
    'spinnaker.blesk',
    'spinnaker.fastproperties',
    'spinnaker.accountLabelColor.directive'
])
  .run(function($state, $rootScope, $log, $exceptionHandler, cacheInitializer, $modalStack, pageTitleService, settings) {
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

    $rootScope.feature = settings.feature;
  })
  .config(function($animateProvider) {
    $animateProvider.classNameFilter(/animated/);

  });
