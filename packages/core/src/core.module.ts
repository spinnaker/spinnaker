/* eslint-disable @spinnaker/import-sort */
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
import 'Select2/select2.css';
import 'select2-bootstrap-css/select2-bootstrap.css';
import 'source-sans-pro/source-sans-pro.css';
import './fonts/icons.css';

import UI_ROUTER from '@uirouter/angularjs';
import '@uirouter/angularjs/release/stateEvents';
import { UI_ROUTER_REACT_HYBRID } from '@uirouter/react-hybrid';

import { RECENT_HISTORY_SERVICE } from './history/recentHistory.service';

import './analytics/GoogleAnalyticsInitializer';
import { UI_ROUTER_REACT_ERROR_BOUNDARY } from './presentation/SpinErrorBoundary';
import { ANALYTICS_MODULE } from './analytics/angulartics.module';
import { APPLICATION_BOOTSTRAP_MODULE } from './bootstrap';
import { APPLICATION_MODULE } from './application/application.module';
import { ARTIFACT_MODULE } from './artifact/artifact.module';
import { AUTHENTICATION_MODULE } from './authentication/authentication.module';
import { CI_MODULE } from './ci/ci.module';
import { CLOUD_PROVIDER_MODULE } from './cloudProvider/cloudProvider.module';
import { CLUSTER_MODULE } from './cluster/cluster.module';
import { CUSTOM_BANNER_CONFIG } from './application/config/customBanner/customBannerConfig.component';

import { DEBUG_WINDOW } from './utils/consoleDebug';
import { DEPLOYMENT_STRATEGY_MODULE } from './deploymentStrategy/deploymentStrategy.module';
import { DIFF_MODULE } from './diffs';
import { ENTITY_TAGS_MODULE } from './entityTag/entityTags.module';
import { HEALTH_COUNTS_MODULE } from './healthCounts/healthCounts.module';
import { HELP_MODULE } from './help/help.module';
import { INSIGHT_MODULE } from './insight/insight.module';
import { INTERCEPTOR_MODULE } from './interceptor/interceptor.module';
import { LOAD_BALANCER_MODULE } from './loadBalancer/loadBalancer.module';
import { MANAGED_RESOURCE_CONFIG } from './application/config/managedResources/ManagedResourceConfig';
import { MANAGED_RESOURCES_DATA_SOURCE, CORE_MANAGED_MANAGED_MODULE } from './managed';
import { FUNCTION_MODULE } from './function/function.module';

import { NETWORK_INTERCEPTOR } from './api/network.interceptor';

import { PAGE_TITLE_MODULE } from './pageTitle/pageTitle.module';
import { PAGER_DUTY_MODULE } from './pagerDuty/pagerDuty.module';
import { PIPELINE_MODULE } from './pipeline/pipeline.module';
import { PIPELINE_TEMPLATE_MODULE } from './pipeline/config/templates/pipelineTemplate.module';
import { PLUGINS_MODULE } from './plugins';
import { REACT_MODULE } from './reactShims';
import { initGoogleAnalytics } from './reactShims/react.ga';

import { REGION_MODULE } from './region/region.module';
import { SERVERGROUP_MODULE } from './serverGroup/serverGroup.module';
import { SERVER_GROUP_MANAGER_MODULE } from './serverGroupManager/serverGroupManager.module';
import { SLACK_COMPONENT } from './slack';
import { STYLEGUIDE_MODULE } from './styleguide/styleguide.module';
import { SUBNET_MODULE } from './subnet/subnet.module';

import { FIREWALL_LABEL_COMPONENT } from './securityGroup/label/firewallLabel.component';

import { LABEL_FILTER_COMPONENT } from './cluster/filter/labelFilter.component';
import { FILTER_SEARCH_COMPONENT } from './cluster/filter/filterSearch.component';

import { WIDGETS_MODULE } from './widgets/widgets.module';

import * as State from './state';
import { CORE_FORMS_FORMS_MODULE } from './forms/forms.module';
import { CORE_INSTANCE_INSTANCE_MODULE } from './instance/instance.module';
import { CORE_MODAL_MODAL_MODULE } from './modal/modal.module';
import { CORE_NOTIFICATION_NOTIFICATIONS_MODULE } from './notification/notifications.module';
import { CORE_PRESENTATION_PRESENTATION_MODULE } from './presentation/presentation.module';
import { CORE_PROJECTS_PROJECTS_MODULE } from './projects/projects.module';
import { CORE_SEARCH_SEARCH_MODULE } from './search/search.module';
import { CORE_SECURITYGROUP_SECURITYGROUP_MODULE } from './securityGroup/securityGroup.module';
import { CORE_TASK_TASK_MODULE } from './task/task.module';
import { CORE_UTILS_UTILS_MODULE } from './utils/utils.module';
import { CORE_VALIDATION_VALIDATION_MODULE } from './validation/validation.module';
import { CORE_BANNER_CONTAINER_MODULE } from './banner/bannerContainer.module';
import ANGULAR_MESSAGES from 'angular-messages';
import ANGULAR_SANITIZE from 'angular-sanitize';
import { angularSpinner } from 'angular-spinner';
import ANGULAR_UI_BOOTSTRAP from 'angular-ui-bootstrap';
import UI_SELECT from 'ui-select';

initGoogleAnalytics();

const UI_ROUTER_STATE_EVENTS_SHIM = 'ui.router.state.events';
export const CORE_MODULE = 'spinnaker.core';
module(CORE_MODULE, [
  ANGULAR_MESSAGES,
  ANGULAR_SANITIZE,
  UI_ROUTER,
  UI_ROUTER_STATE_EVENTS_SHIM,
  UI_ROUTER_REACT_HYBRID,
  UI_ROUTER_REACT_ERROR_BOUNDARY,
  REACT_MODULE, // must precede modules which register states
  ANGULAR_UI_BOOTSTRAP as any,
  UI_SELECT,
  angularSpinner.name,

  ANALYTICS_MODULE,
  APPLICATION_MODULE,
  APPLICATION_BOOTSTRAP_MODULE,
  ARTIFACT_MODULE,
  AUTHENTICATION_MODULE,

  CI_MODULE,
  CLOUD_PROVIDER_MODULE,
  CLUSTER_MODULE,
  CUSTOM_BANNER_CONFIG,

  DEBUG_WINDOW,
  DEPLOYMENT_STRATEGY_MODULE,
  DIFF_MODULE,

  ENTITY_TAGS_MODULE,

  FIREWALL_LABEL_COMPONENT,
  CORE_FORMS_FORMS_MODULE,

  HEALTH_COUNTS_MODULE,
  HELP_MODULE,

  INSIGHT_MODULE,
  CORE_INSTANCE_INSTANCE_MODULE,
  INTERCEPTOR_MODULE,

  LABEL_FILTER_COMPONENT,
  FILTER_SEARCH_COMPONENT,
  LOAD_BALANCER_MODULE,
  FUNCTION_MODULE,
  MANAGED_RESOURCE_CONFIG,
  MANAGED_RESOURCES_DATA_SOURCE,
  CORE_MANAGED_MANAGED_MODULE,
  CORE_MODAL_MODAL_MODULE,

  NETWORK_INTERCEPTOR,

  CORE_NOTIFICATION_NOTIFICATIONS_MODULE,

  PAGE_TITLE_MODULE,
  PAGER_DUTY_MODULE,
  PIPELINE_TEMPLATE_MODULE,
  PIPELINE_MODULE,
  PLUGINS_MODULE,
  CORE_PRESENTATION_PRESENTATION_MODULE,
  CORE_PROJECTS_PROJECTS_MODULE,

  RECENT_HISTORY_SERVICE,
  REGION_MODULE,

  CORE_SEARCH_SEARCH_MODULE,
  CORE_SECURITYGROUP_SECURITYGROUP_MODULE,
  SERVERGROUP_MODULE,
  SERVER_GROUP_MANAGER_MODULE,
  SLACK_COMPONENT,
  STYLEGUIDE_MODULE,
  SUBNET_MODULE,

  CORE_TASK_TASK_MODULE,

  CORE_UTILS_UTILS_MODULE,

  WIDGETS_MODULE,

  CORE_VALIDATION_VALIDATION_MODULE,
  CORE_BANNER_CONTAINER_MODULE,
]).run(() => {
  // initialize all the stateful services
  State.initialize();
});
