import { module } from 'angular';

import 'bootstrap/dist/css/bootstrap.css';
import 'jquery-ui';
// Must come after jquery-ui - we want the bootstrap tooltip, JavaScript is fun
import 'bootstrap/dist/js/bootstrap.js';

import 'font-awesome/css/font-awesome.css';
import 'react-select/dist/react-select.css';
import 'ui-select/dist/select.css';

import UI_ROUTER from '@uirouter/angularjs';
require('@uirouter/angularjs/release/stateEvents');
const UI_ROUTER_STATE_SHIM = 'ui.router.state.events';

// use require instead of import to ensure insertion order is preserved
require('Select2/select2.css');
require('select2-bootstrap-css/select2-bootstrap.css');
import 'source-sans-pro';
require('root/app/fonts/spinnaker/icons.css');

import { ANALYTICS_MODULE } from './analytics/analytics.module';
import { APPLICATION_BOOTSTRAP_MODULE } from './bootstrap';
import { APPLICATION_MODULE } from './application/application.module';
import { AUTHENTICATION_MODULE } from './authentication/authentication.module';
import { CANCEL_MODAL_MODULE } from './cancelModal/cancelModal.module';
import { CLOUD_PROVIDER_MODULE } from './cloudProvider/cloudProvider.module';
import { CONFIG_MODULE } from './config/config.module';

import { DEPLOYMENT_STRATEGY_MODULE } from './deploymentStrategy/deploymentStrategy.module';
import { DIFF_MODULE } from './diffs';
import { ENTITY_TAGS_MODULE } from './entityTag/entityTags.module';
import { HEALTH_COUNTS_MODULE } from './healthCounts/healthCounts.module';
import { HELP_MODULE } from './help/help.module';
import { INSIGHT_NGMODULE } from './insight/insight.module';
import { INTERCEPTOR_MODULE } from './interceptor/interceptor.module';
import { LOAD_BALANCER_MODULE } from './loadBalancer/loadBalancer.module';
import { PAGE_TITLE_MODULE } from './pageTitle/pageTitle.module';
import { PIPELINE_TEMPLATE_MODULE } from './pipeline/config/templates/pipelineTemplate.module';
import { REACT_MODULE } from './reactShims';
import { REGION_MODULE } from './region/region.module';
import { SUBNET_MODULE } from './subnet/subnet.module';
import { WHATS_NEW_MODULE } from './whatsNew/whatsNew.module';
import { WIDGETS_MODULE } from './widgets/widgets.module';


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
  UI_ROUTER_STATE_SHIM,
  require('angular-ui-bootstrap'),
  require('ui-select'),
  require('angular-spinner').angularSpinner.name,

  ANALYTICS_MODULE,
  APPLICATION_MODULE,
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

  LOAD_BALANCER_MODULE,

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
]);

