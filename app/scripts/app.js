'use strict';

/**
 * @ngdoc overview
 * @name spinnaker
 * @description
 * # spinnaker
 *
 * Main module of the application.
 */
//BEN_TODO figure out what actually gets used here
global.$ = global.jQuery = require('jquery'); //  deck is reliant on my jquery features we need to load it before angular.

global.Spinner = require('spin.js');

require('jquery-ui');
require('bootstrap/dist/css/bootstrap.css');
require('select2-bootstrap-css/select2-bootstrap.css');
require('Select2/select2.css');
require('ui-select/dist/select.css');

require('angular-wizard/dist/angular-wizard.css');

require('source-sans-pro');


// likely that some of these can be moved to the modules that support them
require('../styles/application.less');
require('../styles/counters.less');
require('../styles/delivery.less');
require('../styles/details.less');
require('../styles/fastProperties.less');
require('../styles/instanceSelection.less');
require('../styles/main.less');
require('../styles/modals.less');
require('../styles/navigation.less');
require('../styles/newapplication.less');
require('../styles/pipelines.less');
require('../styles/rollups.less');
require('../styles/tasks.less');
require('../../utils/stickyHeader/stickyHeader.less');

require('../styles/imports/commonImports.less');
require('./modules/search/global/globalSearch.less');
require('./modules/confirmationModal/confirmationModal.less');

require('../fonts/spinnaker/icons.css');

require('select2');

let angular = require('angular');

require('bootstrap/dist/js/bootstrap.js');


module.exports = angular.module('spinnaker', [
    require('angular-sanitize'),
    require('utils/timeFormatters.js'),
    require('exports?"ui.select"!ui-select'),
    require('exports?"angulartics"!angulartics'),
    require('angular-animate'),
    require('angular-ui-router'),
    require('exports?"ui.bootstrap"!angular-bootstrap'),
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('./filters/filters.module.js'),
    require('imports?define=>false!exports?"angularSpinner"!angular-spinner'),

    require('exports?"angular.filter"!angular-filter'),
    require('./providers/states.js'),
    require('./modules/caches/cacheInitializer.js'),
    require('./modules/delivery/states.js'),
    require('exports?"infinite-scroll"!ng-infinite-scroll/build/ng-infinite-scroll.js'),
    require('./directives/directives.module.js'),

    require('./modules/insight/insight.module.js'),
    require('./modules/applications/application.module.js'),
    require('./modules/feedback/feedback.module.js'),

    require('utils/stickyHeader/stickyHeader.directive.js'),

    require('./modules/loadBalancers/configure/aws/loadBalancer.transformer.service.js'),
    require('./modules/loadBalancers/configure/gce/loadBalancer.transformer.service.js'),

    require('./modules/aws.module.js'),
    require('./modules/gce.module.js'),
    require('./modules/subnet/subnet.module.js'),
    require('utils/utils.module.js'),
    require('./modules/caches/caches.module.js'),
    require('./modules/naming/naming.service.js'),
    require('./modules/delegation/serviceDelegate.service.js'),
    require('./modules/healthCounts/healthCounts.directive.js'),
    require('./settings/settings.js'),
    require('./modules/scheduler/scheduler.service.js'),
    require('./services/urlbuilder.js'),
    require('./modules/clusterFilter/cluster.filter.module.js'),
    require('./directives/modalWizard.js'),
    require('./modules/confirmationModal/confirmationModal.service.js'),
    require('./modules/common/ajaxError.interceptor.js'),
    require('./modules/deploymentStrategy/deploymentStrategy.module.js'),
    require('./modules/deploymentStrategy/strategies/redblack/redblack.strategy.module.js'),
    require('./modules/deploymentStrategy/strategies/none/none.strategy.module.js'),
    require('./modules/deploymentStrategy/strategies/highlander/highlander.strategy.module.js'),
    require('./modules/deploymentStrategy/strategies/rollingPush/rollingPush.strategy.module.js'),
    require('./modules/serverGroups/configure/common/transformer/serverGroup.transformer.service.js'),
    require('./modules/serverGroups/serverGroup.module.js'),
    require('./modules/securityGroups/securityGroup.module.js'),
    require('./modules/instance/instance.module.js'),
    require('./modules/pageTitle/pageTitleService.js'),
    require('./modules/help/help.module.js'),
    require('./modules/delivery/delivery.module.js'),
    require('./modules/pipelines/pipelines.module.js'),
    require('./modules/pipelines/config/stages/bake/bakeStage.module.js'),
    require('./modules/pipelines/config/stages/core/stage.core.module.js'),
    require('./modules/pipelines/config/stages/deploy/deployStage.module.js'),
    require('./modules/pipelines/config/stages/destroyAsg/destroyAsgStage.module.js'),
    require('./modules/pipelines/config/stages/disableAsg/disableAsgStage.module.js'),
    require('./modules/pipelines/config/stages/enableAsg/enableAsgStage.module.js'),
    require('./modules/pipelines/config/stages/executionWindows/executionWindowsStage.module.js'),
    require('./modules/pipelines/config/stages/findAmi/findAmiStage.module.js'),
    require('./modules/pipelines/config/stages/jenkins/jenkinsStage.module.js'),
    require('./modules/pipelines/config/stages/manualJudgment/manualJudgmentStage.module.js'),
    require('./modules/pipelines/config/stages/modifyScalingProcess/modifyScalingProcess.module.js'),
    require('./modules/pipelines/config/stages/pipeline/pipelineStage.module.js'),
    require('./modules/pipelines/config/stages/quickPatchAsg/quickPatchAsgStage.module.js'),
    require('./modules/pipelines/config/stages/resizeAsg/resizeAsgStage.module.js'),
    require('./modules/pipelines/config/stages/script/scriptStage.module.js'),
    require('./modules/pipelines/config/stages/wait/waitStage.module.js'),
    require('./modules/pipelines/config/stages/determineTargetReference/determineTargetReference.module.js'),
    require('./modules/authentication/authentication.module.js'),
    require('./modules/search/search.module.js'),
    require('./modules/notifications/notifications.module.js'),
    require('./modules/notifications/types/email/email.notification.type.module.js'),
    require('./modules/notifications/types/hipchat/hipchat.notification.type.module.js'),
    require('./modules/notifications/types/sms/sms.notification.type.module.js'),
    require('./modules/tasks/tasks.module.js'),
    require('./modules/tasks/monitor/taskMonitor.module.js'),
    require('./modules/validation/validation.module.js'),
    require('./modules/loadBalancers/loadBalancers.module.js'),
    require('./modules/vpc/vpc.module.js'),
    require('./modules/keyPairs/keyPairs.module.js'),
    require('./modules/config/config.module.js'),
    require('./directives/gist.directive.js'),
    require('./modules/whatsNew/whatsNew.directive.js'),
    require('./directives/help.directive.js'),
    require('./modules/networking/networking.module.js'),
    require('./modules/blesk/blesk.module.js'),
    require('./modules/fastProperties/fastProperties.module.js'),
    require('./directives/accountLabelColor.directive.js'),
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
      });
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

  })
  .config(function ($logProvider, statesProvider ) {
    statesProvider.setStates();
    $logProvider.debugEnabled(true);
  })
  //.config(function ($compileProvider) {
  //  $compileProvider.debugInfoEnabled(false);
  //})
  .config(function(uiSelectConfig) {
    uiSelectConfig.theme = 'select2';
  })
  .config(function($tooltipProvider) {
    $tooltipProvider.options({
      appendToBody: true
    });
    $tooltipProvider.setTriggers({
      'mouseenter focus': 'mouseleave blur'
    });
  })
  .config(function($modalProvider) {
    $modalProvider.options.backdrop = 'static';
    $modalProvider.options.keyboard = false;
  })
  .config(function(RestangularProvider, settings) {
    RestangularProvider.setBaseUrl(settings.gateUrl);
  })
  .config(function($httpProvider){
    $httpProvider.interceptors.push('ajaxErrorInterceptor');
    $httpProvider.defaults.headers.patch = {
      'Content-Type': 'application/json;charset=utf-8'
    };
  })
  .config(function($provide) {
    $provide.decorator('$exceptionHandler', function ($delegate, $analytics) {
      return function (exception, cause) {
        try {
          var action = 'msg: ' + exception.message + ' url: ' + window.location;
          var label = exception.stack;

          $analytics.eventTrack(action, {category: 'JavaScript Error', label: label, noninteraction: true});
          $delegate(exception, cause);
        } catch(e) {
          // eat it to permit a endless exception loop from happening
        }
      };
    });
  })
  .config(require('./decorators/uiSelectDecorator.js'))
  //.config(function ($compileProvider) {
  //  $compileProvider.debugInfoEnabled(false);
  //})
  .config(function(uiSelectConfig) {
    uiSelectConfig.theme = 'select2';
    uiSelectConfig.appendToBody = true;
  })
  .config(function($tooltipProvider) {
    $tooltipProvider.options({
      appendToBody: true
    });
    $tooltipProvider.setTriggers({
      'mouseenter focus': 'mouseleave blur'
    });
  })
  .config(function($modalProvider) {
    $modalProvider.options.backdrop = 'static';
    $modalProvider.options.keyboard = false;
  })
  .config(function(RestangularProvider, settings) {
    RestangularProvider.setBaseUrl(settings.gateUrl);
  })
  .config(function($httpProvider){
    $httpProvider.interceptors.push('ajaxErrorInterceptor');
    $httpProvider.defaults.headers.patch = {
      'Content-Type': 'application/json;charset=utf-8'
    };
  })
  .config(function($provide) {
    $provide.decorator('$exceptionHandler', function ($delegate, $analytics) {
      return function (exception, cause) {
        try {
          var action = 'msg: ' + exception.message + ' url: ' + window.location;
          var label = exception.stack;

          $analytics.eventTrack(action, {category: 'JavaScript Error', label: label, noninteraction: true});
          $delegate(exception, cause);
        } catch(e) {
          // eat it to permit a endless exception loop from happening
        }
      };
    });
  })
  .run(function($templateCache) {
    $templateCache.put('template/popover/popover.html',
        '<div class="popover {{placement}}" ng-class="{ in: isOpen(), fade: animation() }">\n' +
        '  <div class="arrow"></div>\n' +
        '\n' +
        '  <div class="popover-inner">\n' +
        '      <h3 class="popover-title" ng-bind="title" ng-show="title"></h3>\n' +
        '      <div class="popover-content" ng-bind-html="content"></div>\n' +
        '  </div>\n' +
        '</div>\n' +
        '');
  });

