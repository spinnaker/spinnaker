import { module } from 'angular';

import './applicationSearchResultType';
import './nav/defaultCategories';
import { APPLICATION_STATE_PROVIDER } from './application.state.provider';
import { APPLICATIONS_STATE_PROVIDER } from './applications.state.provider';
import { PERMISSIONS_CONFIGURER_COMPONENT } from './modal/permissionsConfigurer.component';
import './modal/upsertApplication.help';

export const APPLICATION_MODULE = 'spinnaker.core.application';
module(APPLICATION_MODULE, [
  APPLICATION_STATE_PROVIDER,
  APPLICATIONS_STATE_PROVIDER,
  require('./config/applicationConfig.controller').name,
  require('./modal/createApplication.modal.controller').name,
  require('./modal/platformHealthOverride.directive').name,
  require('./config/appConfig.dataSource').name,
  PERMISSIONS_CONFIGURER_COMPONENT,
]);
