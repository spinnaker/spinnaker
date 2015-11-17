'use strict';


global.Spinner = require('spin.js');

let angular = require('angular');

require('../../../fonts/spinnaker/icons.css');

require('Select2');
require('jquery-ui');
// Must come after jquery-ui - we want the bootstrap tooltip, JavaScript is fun
require('bootstrap/dist/js/bootstrap.js');
require('bootstrap/dist/css/bootstrap.css');
require('select2-bootstrap-css/select2-bootstrap.css');
require('Select2/select2.css');
require('ui-select/dist/select.css');

require('angular-wizard/dist/angular-wizard.css');

require('source-sans-pro');

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});


module.exports = angular
  .module('spinnaker.core', [
    require('angular-animate'),
    require('angular-messages'),
    require('angular-sanitize'),
    require('angular-ui-router'),
    require('angular-ui-bootstrap'),
    require('exports?"angular.filter"!angular-filter'),
    require('exports?"infinite-scroll"!ng-infinite-scroll/build/ng-infinite-scroll.js'),
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('exports?"ui.select"!ui-select'),
    require('imports?define=>false!exports?"angularSpinner"!angular-spinner'),

    require('./application/application.module.js'),

    require('./account/accountLabelColor.directive.js'),
    require('./authentication/authentication.module.js'),

    require('./bootstrap/applicationBootstrap.directive.js'),

    require('./cache/caches.module.js'),
    require('./cloudProvider/cloudProviderLogo.directive.js'),
    require('./cloudProvider/cloudProviderLabel.directive.js'),
    require('./cloudProvider/serviceDelegate.service.js'),
    require('./cluster/cluster.module.js'),
    require('./config/settings.js'),
    require('./confirmationModal/confirmationModal.service.js'),

    require('./delivery/delivery.module.js'),
    require('./deploymentStrategy/deploymentStrategy.module.js'),
    require('./deploymentStrategy/strategies/highlander/highlander.strategy.module.js'),
    require('./deploymentStrategy/strategies/none/none.strategy.module.js'),
    require('./deploymentStrategy/strategies/redblack/redblack.strategy.module.js'),
    require('./deploymentStrategy/strategies/custom/custom.strategy.module.js'),
    require('./deploymentStrategy/strategies/rollingPush/rollingPush.strategy.module.js'),

    require('./forms/forms.module.js'),

    require('./healthCounts/healthCounts.directive.js'),
    require('./help/help.module.js'),
    require('./history/recentHistory.service.js'),

    require('./insight/insight.module.js'),
    require('./instance/instance.module.js'),

    require('./loadBalancer/loadBalancer.module.js'),

    require('./modal/modal.module.js'),

    require('./naming/naming.service.js'),
    require('./navigation/states.provider.js'),
    require('./notification/notifications.module.js'),
    require('./notification/types/email/email.notification.type.module.js'),
    require('./notification/types/hipchat/hipchat.notification.type.module.js'),
    require('./notification/types/slack/slack.notification.type.module.js'),
    require('./notification/types/sms/sms.notification.type.module.js'),

    require('./pageTitle/pageTitle.service.js'),

    require('./pipeline/pipelines.module.js'),
    require('./pipeline/config/stages/bake/bakeStage.module.js'),
    require('./pipeline/config/stages/checkPreconditions/checkPreconditionsStage.module.js'),
    require('./pipeline/config/stages/core/stage.core.module.js'),
    require('./pipeline/config/stages/deploy/deployStage.module.js'),
    require('./pipeline/config/stages/destroyAsg/destroyAsgStage.module.js'),
    require('./pipeline/config/stages/determineTargetReference/determineTargetReference.module.js'),
    require('./pipeline/config/stages/disableAsg/disableAsgStage.module.js'),
    require('./pipeline/config/stages/disableCluster/disableClusterStage.module.js'),
    require('./pipeline/config/stages/enableAsg/enableAsgStage.module.js'),
    require('./pipeline/config/stages/executionWindows/executionWindowsStage.module.js'),
    require('./pipeline/config/stages/findAmi/findAmiStage.module.js'),
    require('./pipeline/config/stages/jenkins/jenkinsStage.module.js'),
    require('./pipeline/config/stages/manualJudgment/manualJudgmentStage.module.js'),
    require('./pipeline/config/stages/pipeline/pipelineStage.module.js'),
    require('./pipeline/config/stages/resizeAsg/resizeAsgStage.module.js'),
    require('./pipeline/config/stages/scaleDownCluster/scaleDownClusterStage.module.js'),
    require('./pipeline/config/stages/script/scriptStage.module.js'),
    require('./pipeline/config/stages/shrinkCluster/shrinkClusterStage.module.js'),
    require('./pipeline/config/stages/wait/waitStage.module.js'),
    require('./pipeline/config/stages/waitForParentTasks/waitForParentTasks.js'),
    require('./pipeline/config/preconditions/preconditions.module.js'),
    require('./pipeline/config/preconditions/types/clusterSize/clusterSize.precondition.type.module.js'),
    require('./pipeline/config/preconditions/types/expression/expression.precondition.type.module.js'),
    require('./presentation/presentation.module.js'),

    require('./scheduler/scheduler.service.js'),
    require('./search/search.module.js'),
    require('./securityGroup/securityGroup.module.js'),
    require('./serverGroup/serverGroup.module.js'),

    require('./task/task.module.js'),

    require('./templateOverride/templateOverride.registry.js'),

    require('./utils/utils.module.js'),

    require('./validation/validation.module.js'),
  ])
  .run(function($rootScope, $log, $state, settings) {

    $rootScope.feature = settings.feature;

    $rootScope.$state = $state; // TODO: Do we really need this?

    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      $log.debug(event.name, {
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
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
    });

    $rootScope.$on('$stateChangeSuccess', function(event, toState, toParams, fromState, fromParams) {
      $log.debug(event.name, {
        event: event,
        toState: toState,
        toParams: toParams,
        fromState: fromState,
        fromParams: fromParams
      });
    });
  })
  .run(function($templateCache) {
    $templateCache.put('template/popover/popover.html',
      '<div tooltip-animation-class="fade"' +
      '  uib-tooltip-classes' +
      '  ng-class="{ in: isOpen() }">' +
      '  <div class="arrow"></div>' +
      '  <div class="popover-inner">' +
      '      <h3 class="popover-title" ng-bind="title" ng-if="title"></h3>' +
      '      <div class="popover-content" ng-bind-html="content"></div>' +
      '  </div>' +
      '  </div>');
  })
  .config(function ($logProvider, statesProvider) {
    statesProvider.setStates();
    $logProvider.debugEnabled(true);
  })
  .config(function($uibTooltipProvider) {
    $uibTooltipProvider.options({
      appendToBody: true
    });
    $uibTooltipProvider.setTriggers({
      'mouseenter focus': 'mouseleave blur'
    });
  })
  .config(function($modalProvider) {
    $modalProvider.options.backdrop = 'static';
    $modalProvider.options.keyboard = false;
  })
  .config(function(RestangularProvider, settings) {
    RestangularProvider.setBaseUrl(settings.gateUrl);
    RestangularProvider.setDefaultHttpFields({timeout: settings.pollSchedule * 2 + 5000}); // TODO: replace with apiHost call
  })
  .config(function($httpProvider){
    $httpProvider.defaults.headers.patch = {
      'Content-Type': 'application/json;charset=utf-8'
    };
  })
  .config(function($compileProvider) {
    $compileProvider.aHrefSanitizationWhitelist(/^\s*(https?|mailto|hipchat):/);
  })
  .config(function($animateProvider) {
    $animateProvider.classNameFilter(/animated/);
  })
  .config(require('./forms/uiSelect.decorator.js'))
  .config(function(uiSelectConfig) {
    uiSelectConfig.theme = 'select2';
    uiSelectConfig.appendToBody = true;
  })

  .name;
