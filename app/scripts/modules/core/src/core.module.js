'use strict';

const angular = require('angular');

import 'bootstrap/dist/css/bootstrap.css';
import 'jquery-ui';
// Must come after jquery-ui - we want the bootstrap tooltip, JavaScript is fun
import 'bootstrap/dist/js/bootstrap.js';

import 'font-awesome/css/font-awesome.css';
import 'react-select/dist/react-select.css';
import 'Select2';
import 'select2-bootstrap-css/select2-bootstrap.css';
import 'Select2/select2.css';

import 'source-sans-pro';
import Spinner from 'spin.js';
import 'ui-select/dist/select.css';

import { ANALYTICS_MODULE } from './analytics/analytics.module';
import { APPLICATION_BOOTSTRAP_MODULE } from './bootstrap/applicationBootstrap.module';
import { AUTHENTICATION_MODULE } from './authentication/authentication.module';
import { CANCEL_MODAL_MODULE } from './cancelModal/cancelModal.module';
import { CLOUD_PROVIDER_MODULE } from './cloudProvider/cloudProvider.module';
import { CONFIG_MODULE } from './config/config.module';
import { SETTINGS } from './config/settings';

import { DEPLOYMENT_STRATEGY_MODULE } from './deploymentStrategy/deploymentStrategy.module';
import { DIFF_MODULE } from './diffs';
import { ENTITY_TAGS_MODULE } from './entityTag/entityTags.module';
import { HEALTH_COUNTS_MODULE } from './healthCounts/healthCounts.module';
import { HELP_MODULE } from './help/help.module';
import { INSIGHT_NGMODULE } from './insight/insight.module';
import { INTERCEPTOR_MODULE } from './interceptor/interceptor.module';
import { PAGE_TITLE_MODULE } from './pageTitle/pageTitle.module';
import { PIPELINE_TEMPLATE_MODULE } from './pipeline/config/templates/pipelineTemplate.module';
import { REACT_MODULE } from './reactShims';
import { REGION_MODULE } from './region/region.module';
import { UI_ROUTER_STATE_SHIM } from './routing/uirouter.stateEvents.shim';
import { SUBNET_MODULE } from './subnet/subnet.module';
import { WHATS_NEW_MODULE } from './whatsNew/whatsNew.module';
import { WIDGETS_MODULE } from './widgets/widgets.module';

require('root/app/fonts/spinnaker/icons.css');

// TODO: Move this value into this module when core.module gets converted to TS
import { CORE_MODULE } from './index';

// load all templates into the $templateCache
var templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

module.exports = angular
  .module(CORE_MODULE, [
    require('angular-messages'),
    require('angular-sanitize'),
    require('angular-ui-router').default,
    UI_ROUTER_STATE_SHIM,
    require('angular-ui-bootstrap'),
    require('ui-select'),
    require('angular-spinner').angularSpinner.name,

    ANALYTICS_MODULE,
    require('./application/application.module'),
    APPLICATION_BOOTSTRAP_MODULE,
    AUTHENTICATION_MODULE,


    require('./cache/caches.module'),
    CANCEL_MODAL_MODULE,
    CLOUD_PROVIDER_MODULE,
    CONFIG_MODULE,
    require('./cluster/cluster.module'),

    DEPLOYMENT_STRATEGY_MODULE,
    require('./delivery/delivery.module'),
    DIFF_MODULE,

    ENTITY_TAGS_MODULE,

    require('./forms/forms.module'),

    HEALTH_COUNTS_MODULE,
    HELP_MODULE,

    INSIGHT_NGMODULE.name,
    require('./instance/instance.module'),
    INTERCEPTOR_MODULE,

    require('./loadBalancer/loadBalancer.module'),

    require('./modal/modal.module'),

    require('./notification/notifications.module'),

    PAGE_TITLE_MODULE,
    PIPELINE_TEMPLATE_MODULE,
    require('./pipeline/pipelines.module'),
    require('./presentation/presentation.module'),
    require('./projects/projects.module'),

    REACT_MODULE,
    REGION_MODULE,

    require('./search/search.module'),
    require('./securityGroup/securityGroup.module'),
    require('./serverGroup/serverGroup.module'),
    SUBNET_MODULE,

    require('./task/task.module'),

    require('./utils/utils.module'),

    WHATS_NEW_MODULE,
    WIDGETS_MODULE,

    require('./validation/validation.module'),
  ])
  .run(function($rootScope, $log, $state) {
    window.Spinner = Spinner;

    $rootScope.feature = SETTINGS.feature;

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
  .run($trace => $trace.enable(1))
  .run(function (cacheInitializer) {
    cacheInitializer.initialize();
  })
  .config(function ($logProvider) {
    $logProvider.debugEnabled(SETTINGS.debugEnabled);
  })
  .config(function($uibTooltipProvider) {
    $uibTooltipProvider.options({
      appendToBody: true
    });
    $uibTooltipProvider.setTriggers({
      'mouseenter focus': 'mouseleave blur'
    });
  })
  .config(function($uibModalProvider) {
    $uibModalProvider.options.backdrop = 'static';
    $uibModalProvider.options.keyboard = false;
  })
  .config(function($httpProvider) {
    $httpProvider.defaults.headers.patch = {
      'Content-Type': 'application/json;charset=utf-8'
    };
  })
  .config(function($qProvider) {
    // Angular 1.6 stops suppressing unhandle rejections on promises. This resets it back to 1.5 behavior.
    // See https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$q
    $qProvider.errorOnUnhandledRejections(false);
  })
  .config(function($compileProvider) {
    $compileProvider.aHrefSanitizationWhitelist(/^\s*(https?|mailto|hipchat|slack|ssh):/);
    // Angular 1.6 defaults preAssignBindingsEnabled to false, reset to true to mimic 1.5 behavior.
    // See https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$compile
    $compileProvider.preAssignBindingsEnabled(true);
  })
  .config(function($locationProvider) {
    // Angular 1.6 sets default hashPrefix to '!', change it back to ''
    // See https://docs.angularjs.org/guide/migration#migrate1.5to1.6-ng-services-$location
    $locationProvider.hashPrefix('');
  })
  .config(require('./forms/uiSelect.decorator'))
  .config(function(uiSelectConfig) {
    uiSelectConfig.theme = 'select2';
    uiSelectConfig.appendToBody = true;
  })
  .run(function($stateRegistry, $uiRouter) {
    function launchVisualizer() {
      const collapsedStates = [
        'home.data',
        'home.project',
        'home.projects',
        'home.applications',
        'home.applications.application.insight',
        'home.applications.application.insight.clusters',
        'home.applications.application.insight.securityGroups',
        'home.applications.application.insight.loadBalancers',
        'home.applications.application.pipelines',
        'home.project.application.insight',
        'home.project.application.insight.clusters',
        'home.project.application.insight.securityGroups',
        'home.project.application.insight.loadBalancers',
        'home.project.application.pipelines',
      ];

      // eslint-disable-next-line no-underscore-dangle
      collapsedStates.forEach(state => $stateRegistry.get(state).$$state()._collapsed = true);

      return System.import('ui-router-visualizer').then(vis => $uiRouter.plugin(vis.Visualizer));
    }

    function toggleVisualizer(enabled) {
      if (enabled) {
        return launchVisualizer();
      } else {
        const plugin = $uiRouter.getPlugin('visualizer');
        plugin && $uiRouter.dispose(plugin);
      }
    }

    // Values allowed: TRANSITION (true), HOOK, RESOLVE, UIVIEW, VIEWCONFIG, or ALL
    function toggleTrace(newValue) {
      let trace = $uiRouter.trace;
      trace.disable();
      if (typeof newValue === 'string') {
        if (newValue.toUpperCase() === 'TRUE') {
          trace.enable('TRANSITION');
        } else if (newValue.toUpperCase() === 'ALL') {
          trace.enable();
        } else {
          let traceValues = newValue.split(',').map(str => str.trim().toUpperCase());
          trace.enable(...traceValues);
        }
      }
    }

    const paramChangedHandler = (paramName, changedHandler) => (transition) => {
      const previousValue = transition.params('from')[paramName];
      const newValue = transition.params('to')[paramName];

      if (previousValue === newValue) {
        return null;
      }
      return changedHandler(newValue, previousValue);
    };

    $uiRouter.transitionService.onBefore({}, paramChangedHandler('vis', toggleVisualizer));
    $uiRouter.transitionService.onBefore({}, paramChangedHandler('trace', toggleTrace));

    // Type javascript:vis() in the browser url or add `&vis=true` to the spinnaker query params
    window.vis = launchVisualizer;
  });

