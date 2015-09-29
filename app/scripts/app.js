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

require('../../node_modules/angular-hotkeys/build/hotkeys.css');

require('jquery-ui');
require('bootstrap/dist/css/bootstrap.css');
require('select2-bootstrap-css/select2-bootstrap.css');
require('Select2/select2.css');
require('ui-select/dist/select.css');

require('angular-wizard/dist/angular-wizard.css');

require('source-sans-pro');
let Clipboard = require('clipboard');

// likely that some of these can be moved to the modules that support them
require('./modules/core/application/application.less');
require('./modules/core/delivery/delivery.less');
require('./modules/core/presentation/details.less');
require('./modules/core/instance/instanceSelection.less');
require('./modules/core/presentation/main.less');
require('./modules/core/modal/modals.less');
require('./modules/core/application/newapplication.less');

require('../fonts/spinnaker/icons.css');

require('Select2');

let angular = require('angular');

require('bootstrap/dist/js/bootstrap.js');

// load all templates into the $templateCache
var templates = require.context('../', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular.module('spinnaker', [
    require('angular-sanitize'),
    require('angular-messages'),
    require('./modules/core/utils/timeFormatters.js'),
    require('exports?"ui.select"!ui-select'),
    require('exports?"cfp.hotkeys"!angular-hotkeys'),
    require('angular-animate'),
    require('angular-ui-router'),
    require('angular-ui-bootstrap'),
    require('exports?"restangular"!imports?_=lodash!restangular'),
    require('./modules/core/presentation/anyFieldFilter/anyField.filter.js'),
    require('./modules/core/presentation/robotToHumanFilter/robotToHuman.filter.js'),
    require('imports?define=>false!exports?"angularSpinner"!angular-spinner'),
    require('./modules/core/bootstrap/applicationBootstrap.directive.js'),

    require('./modules/core/presentation/presentation.module.js'),
    require('./modules/core/forms/forms.module.js'),
    require('./modules/core/modal/modal.module.js'),

    require('exports?"angular.filter"!angular-filter'),
    require('./modules/core/navigation/states.provider.js'),
    require('./modules/core/delivery/states.js'),
    require('exports?"infinite-scroll"!ng-infinite-scroll/build/ng-infinite-scroll.js'),

    require('./modules/core/insight/insight.module.js'),
    require('./modules/core/application/application.module.js'),
    require('./modules/netflix/feedback/feedback.module.js'),

    require('./modules/amazon/aws.module.js'),
    require('./modules/google/gce.module.js'),
    require('./modules/cloudfoundry/cf.module.js'),
    require('./modules/titan/titan.module.js'),
    require('./modules/core/utils/utils.module.js'),
    require('./modules/core/cache/caches.module.js'),
    require('./modules/core/naming/naming.service.js'),
    require('./modules/core/cloudProvider/serviceDelegate.service.js'),
    require('./modules/core/healthCounts/healthCounts.directive.js'),
    require('./modules/core/config/settings.js'),
    require('./modules/core/scheduler/scheduler.service.js'),
    require('./modules/core/confirmationModal/confirmationModal.service.js'),
    require('./modules/core/deploymentStrategy/deploymentStrategy.module.js'),
    require('./modules/core/deploymentStrategy/strategies/redblack/redblack.strategy.module.js'),
    require('./modules/core/deploymentStrategy/strategies/none/none.strategy.module.js'),
    require('./modules/core/deploymentStrategy/strategies/highlander/highlander.strategy.module.js'),
    require('./modules/core/deploymentStrategy/strategies/rollingPush/rollingPush.strategy.module.js'),
    require('./modules/core/serverGroup/serverGroup.module.js'),
    require('./modules/core/securityGroup/securityGroup.module.js'),
    require('./modules/core/instance/instance.module.js'),
    require('./modules/core/pageTitle/pageTitle.service.js'),
    require('./modules/core/help/help.module.js'),
    require('./modules/core/delivery/delivery.module.js'),
    require('./modules/core/pipeline/pipelines.module.js'),
    require('./modules/core/pipeline/config/stages/bake/bakeStage.module.js'),
    require('./modules/core/pipeline/config/stages/canary/canaryStage.module.js'),
    require('./modules/core/pipeline/config/stages/core/stage.core.module.js'),
    require('./modules/core/pipeline/config/stages/deploy/deployStage.module.js'),
    require('./modules/core/pipeline/config/stages/destroyAsg/destroyAsgStage.module.js'),
    require('./modules/core/pipeline/config/stages/disableAsg/disableAsgStage.module.js'),
    require('./modules/core/pipeline/config/stages/enableAsg/enableAsgStage.module.js'),
    require('./modules/core/pipeline/config/stages/executionWindows/executionWindowsStage.module.js'),
    require('./modules/core/pipeline/config/stages/findAmi/findAmiStage.module.js'),
    require('./modules/core/pipeline/config/stages/jenkins/jenkinsStage.module.js'),
    require('./modules/core/pipeline/config/stages/manualJudgment/manualJudgmentStage.module.js'),
    require('./modules/core/pipeline/config/stages/modifyScalingProcess/modifyScalingProcess.module.js'),
    require('./modules/core/pipeline/config/stages/pipeline/pipelineStage.module.js'),
    require('./modules/core/pipeline/config/stages/quickPatchAsg/quickPatchAsgStage.module.js'),
    require('./modules/core/pipeline/config/stages/quickPatchAsg/bulkQuickPatchStage/bulkQuickPatchStage.module.js'),
    require('./modules/core/pipeline/config/stages/resizeAsg/resizeAsgStage.module.js'),
    require('./modules/core/pipeline/config/stages/script/scriptStage.module.js'),
    require('./modules/core/pipeline/config/stages/shrinkCluster/shrinkClusterStage.module.js'),
    require('./modules/core/pipeline/config/stages/wait/waitStage.module.js'),
    require('./modules/core/pipeline/config/stages/determineTargetReference/determineTargetReference.module.js'),
    require('./modules/pipelines/config/stages/checkPreconditions/checkPreconditionsStage.module.js'),
    require('./modules/preconditions/preconditions.module.js'),
    require('./modules/preconditions/types/clusterSize/clusterSize.precondition.type.module.js'),
    require('./modules/core/authentication/authentication.module.js'),
    require('./modules/core/cloudProvider/cloudProviderLogo.directive.js'),
    require('./modules/core/search/search.module.js'),
    require('./modules/core/notification/notifications.module.js'),
    require('./modules/core/notification/types/email/email.notification.type.module.js'),
    require('./modules/core/notification/types/hipchat/hipchat.notification.type.module.js'),
    require('./modules/core/notification/types/sms/sms.notification.type.module.js'),
    require('./modules/core/task/task.module.js'),
    require('./modules/core/task/monitor/taskMonitor.module.js'),
    require('./modules/core/validation/validation.module.js'),
    require('./modules/core/loadBalancer/loadBalancer.module.js'),
    require('./modules/core/cluster/cluster.module.js'),
    require('./modules/netflix/whatsNew/whatsNew.directive.js'),
    require('./modules/netflix/blesk/blesk.module.js'),
    require('./modules/netflix/fastProperties/fastProperties.module.js'),
    require('./modules/netflix/alert/alertHandler.js'),
    require('./modules/core/account/accountLabelColor.directive.js'),
    require('./modules/core/history/recentHistory.service.js'),
    require('./config.js'),
])
  .run(function($state, $rootScope, $log, cacheInitializer, $uibModalStack, pageTitleService, settings, recentHistoryService) {
    // This can go away when the next version of ui-router is available (0.2.11+)
    // for now, it's needed because ui-sref-active does not work on parent states
    // and we have to use ng-class. It's gross.
    //
    cacheInitializer.initialize();

    $rootScope.$state = $state;
    $rootScope.$on('$stateChangeStart', function(event, toState, toParams, fromState, fromParams) {
      if (!fromParams.allowModalToStayOpen) {
        $uibModalStack.dismissAll();
      }
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
      if (toState.data && toState.data.history) {
        recentHistoryService.addItem(toState.data.history.type, toState.name, toParams, toState.data.history.keyParams);
      }
    });

    $rootScope.feature = settings.feature;
  })
  .config(function() {
    /*eslint-disable */
    let clipboard = new Clipboard('.clipboard-btn');
    /*eslint-enable*/
  })
  .config(function(hotkeysProvider) {
    hotkeysProvider.template =
                    `<div class="cfp-hotkeys-container fade" ng-class="{in: helpVisible}" style="display: none;"><div class="cfp-hotkeys">
                      <h4 class="cfp-hotkeys-title" ng-if="!header">{{ title }}</h4>
                      <div ng-bind-html="header" ng-if="header"></div>
                      <div style="display:flex; flex-direction:row; align-items: flex-start; justify-content: center">
                        <div>
                           <table>
                             <tbody>
                              <tr>
                                <td class="cfp-hotkeys-keys">
                                  <span class="cfp-hotkeys-key">/</span>
                                </td>
                                <td>Global Search</td>
                              </tr>
                              <tr ng-repeat="hotkey in hotkeys | filter:{ description: \'!$$undefined$$\', combo: \'+shift+\'}">
                                <td class="cfp-hotkeys-keys">
                                  <span ng-repeat="key in hotkey.format() track by $index" class="cfp-hotkeys-key">{{ key }}</span>
                                </td>
                                <td class="cfp-hotkeys-text">{{ hotkey.description }}</td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                        <div>
                          <table>
                            <tbody>
                              <tr ng-repeat="hotkey in hotkeys | filter:{ description: \'!$$undefined$$\', combo: \'+alt+\'}">
                                <td class="cfp-hotkeys-keys">
                                  <span ng-repeat="key in hotkey.format() track by $index" class="cfp-hotkeys-key">{{ key }}</span>
                                </td>
                                <td class="cfp-hotkeys-text">{{ hotkey.description }}</td>
                              </tr>
                            </tbody>
                          </table>
                        </div>
                      </div>
                      <div ng-bind-html="footer" ng-if="footer"></div>
                      <div class="cfp-hotkeys-close" ng-click="toggleCheatSheet()">×</div>
                    </div></div>`;
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
  }).run(function($state, hotkeys) {
    let globalHotkeys = [
      {
        combo: 'ctrl+shift+a',
        description: "Applications",
        callback: () => $state.go('home.applications'),
      },
      {
        combo: 'ctrl+shift+i',
        description: "Infrastructure",
        callback: () => $state.go('home.infrastructure'),
      },
      {
        combo: 'ctrl+shift+d',
        description: 'Data',
        callback: () => $state.go('home.data'),
      },

    ];

    globalHotkeys.forEach((hotkeyConfig) => hotkeys.add(hotkeyConfig));
  })
;
