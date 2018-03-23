import { module } from 'angular';

import { PAGER_DUTY_MODULE } from 'core/pagerDuty/pagerDuty.module';

import './applicationSearchResultType';
import './nav/defaultCategories';
import { APPLICATION_NAV_COMPONENT } from './nav/applicationNav.component';
import { APPLICATION_NAV_SECONDARY_COMPONENT } from './nav/applicationNavSecondary.component';
import { APPLICATION_STATE_PROVIDER } from './application.state.provider';
import { APPLICATIONS_STATE_PROVIDER } from './applications.state.provider';
import { PERMISSIONS_CONFIGURER_COMPONENT } from './modal/permissionsConfigurer.component';
import { UPSERT_APPLICATION_HELP } from './modal/upsertApplication.help';

export const APPLICATION_MODULE = 'spinnaker.core.application';
module(APPLICATION_MODULE, [
  APPLICATION_STATE_PROVIDER,
  APPLICATIONS_STATE_PROVIDER,
  require('./config/applicationConfig.controller.js').name,
  require('./modal/createApplication.modal.controller.js').name,
  require('./modal/platformHealthOverride.directive').name,
  require('./config/appConfig.dataSource').name,
  APPLICATION_NAV_COMPONENT,
  APPLICATION_NAV_SECONDARY_COMPONENT,
  PAGER_DUTY_MODULE,
  PERMISSIONS_CONFIGURER_COMPONENT,
  UPSERT_APPLICATION_HELP,
]);
