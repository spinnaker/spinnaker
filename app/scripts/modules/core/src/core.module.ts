import { module } from 'angular';

import 'bootstrap/dist/css/bootstrap.css';
import 'jquery-ui';
// Must come after jquery-ui - we want the bootstrap tooltip, JavaScript is fun
import 'bootstrap/dist/js/bootstrap';

import '@fortawesome/fontawesome-free/css/solid.css';
import '@fortawesome/fontawesome-free/css/regular.css';
import '@fortawesome/fontawesome-free/css/fontawesome.css';
import 'react-select/dist/react-select.css';
import 'react-virtualized/styles.css';
import 'react-virtualized-select/styles.css';
import 'ui-select/dist/select.css';
import '@spinnaker/styleguide/public/styleguide.min.css';

import UI_ROUTER from '@uirouter/angularjs';
const UI_ROUTER_STATE_EVENTS_SHIM = 'ui.router.state.events';
require('@uirouter/angularjs/release/stateEvents');
import { UI_ROUTER_REACT_HYBRID } from '@uirouter/react-hybrid';

// use require instead of import to ensure insertion order is preserved
require('Select2/select2.css');
require('select2-bootstrap-css/select2-bootstrap.css');
import 'source-sans-pro';
import { RECENT_HISTORY_SERVICE } from 'core/history';
require('root/app/fonts/spinnaker/icons.css');

import './analytics/GoogleAnalyticsInitializer';
import { ANALYTICS_MODULE } from './analytics/angulartics.module';
import { APPLICATION_BOOTSTRAP_MODULE } from './bootstrap';
import { APPLICATION_MODULE } from './application/application.module';
import { ARTIFACT_MODULE } from './artifact/artifact.module';
import { AUTHENTICATION_MODULE } from './authentication/authentication.module';
import { CLOUD_PROVIDER_MODULE } from './cloudProvider/cloudProvider.module';
import { CLUSTER_MODULE } from './cluster/cluster.module';
import { CUSTOM_BANNER_CONFIG } from './application/config/customBanner/customBannerConfig.component';

import { DEBUG_WINDOW } from './utils/consoleDebug';
import { DEPLOYMENT_STRATEGY_MODULE } from './deploymentStrategy/deploymentStrategy.module';
import { DIFF_MODULE } from './diffs';
import { ENTITY_TAGS_MODULE } from './entityTag/entityTags.module';
import { HEALTH_COUNTS_MODULE } from './healthCounts/healthCounts.module';
import { HELP_MODULE } from './help/help.module';
import { INSIGHT_NGMODULE } from './insight/insight.module';
import { INTERCEPTOR_MODULE } from './interceptor/interceptor.module';
import { LOAD_BALANCER_MODULE } from './loadBalancer/loadBalancer.module';

import { NETWORK_INTERCEPTOR } from './api/network.interceptor';

import { PAGE_TITLE_MODULE } from './pageTitle/pageTitle.module';
import { PAGER_DUTY_MODULE } from 'core/pagerDuty/pagerDuty.module';
import { PIPELINE_MODULE } from './pipeline/pipeline.module';
import { PIPELINE_TEMPLATE_MODULE } from './pipeline/config/templates/pipelineTemplate.module';
import { REACT_MODULE } from './reactShims';
import { REGION_MODULE } from './region/region.module';
import { SERVERGROUP_MODULE } from './serverGroup/serverGroup.module';
import { SERVER_GROUP_MANAGER_MODULE } from './serverGroupManager/serverGroupManager.module';
import { STYLEGUIDE_MODULE } from './styleguide/styleguide.module';
import { SUBNET_MODULE } from './subnet/subnet.module';

import { FIREWALL_LABEL_COMPONENT } from 'core/securityGroup/label/firewallLabel.component';
import { LABEL_FILTER_COMPONENT } from 'core/cluster/filter/labelFilter.component';

import { WHATS_NEW_MODULE } from './whatsNew/whatsNew.module';
import { WIDGETS_MODULE } from './widgets/widgets.module';

import * as State from './state';

// load all templates into the $templateCache
const templates = require.context('./', true, /\.html$/);
templates.keys().forEach(function(key) {
  templates(key);
});

export const CORE_MODULE = 'spinnaker.core';
module(CORE_MODULE, [
  require('angular-messages'),
  require('angular-sanitize'),
  UI_ROUTER,
  UI_ROUTER_STATE_EVENTS_SHIM,
  UI_ROUTER_REACT_HYBRID,
  REACT_MODULE, // must precede modules which register states
  require('angular-ui-bootstrap'),
  require('ui-select'),
  require('angular-spinner').angularSpinner.name,

  ANALYTICS_MODULE,
  APPLICATION_MODULE,
  APPLICATION_BOOTSTRAP_MODULE,
  ARTIFACT_MODULE,
  AUTHENTICATION_MODULE,

  CLOUD_PROVIDER_MODULE,
  CLUSTER_MODULE,
  CUSTOM_BANNER_CONFIG,

  DEBUG_WINDOW,
  DEPLOYMENT_STRATEGY_MODULE,
  DIFF_MODULE,

  ENTITY_TAGS_MODULE,

  FIREWALL_LABEL_COMPONENT,
  require('./forms/forms.module').name,

  HEALTH_COUNTS_MODULE,
  HELP_MODULE,

  INSIGHT_NGMODULE.name,
  require('./instance/instance.module').name,
  INTERCEPTOR_MODULE,

  LABEL_FILTER_COMPONENT,
  LOAD_BALANCER_MODULE,

  require('./modal/modal.module').name,

  NETWORK_INTERCEPTOR,

  require('./notification/notifications.module').name,

  PAGE_TITLE_MODULE,
  PAGER_DUTY_MODULE,
  PIPELINE_TEMPLATE_MODULE,
  PIPELINE_MODULE,
  require('./presentation/presentation.module').name,
  require('./projects/projects.module').name,

  RECENT_HISTORY_SERVICE,
  REGION_MODULE,

  require('./search/search.module').name,
  require('./securityGroup/securityGroup.module').name,
  SERVERGROUP_MODULE,
  SERVER_GROUP_MANAGER_MODULE,
  STYLEGUIDE_MODULE,
  SUBNET_MODULE,

  require('./task/task.module').name,

  require('./utils/utils.module').name,

  WHATS_NEW_MODULE,
  WIDGETS_MODULE,

  require('./validation/validation.module').name,
]).run(() => {
  // initialize all the stateful services
  State.initialize();
});
