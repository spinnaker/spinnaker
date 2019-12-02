import { module } from 'angular';

import './applicationSearchResultType';
import './nav/defaultCategories';
import { APPLICATION_STATE_PROVIDER } from './application.state.provider';
import { APPLICATIONS_STATE_PROVIDER } from './applications.state.provider';
import { PERMISSIONS_CONFIGURER_COMPONENT } from './modal/permissionsConfigurer.component';
import './modal/upsertApplication.help';
import { CORE_APPLICATION_CONFIG_APPLICATIONCONFIG_CONTROLLER } from './config/applicationConfig.controller';
import { CORE_APPLICATION_MODAL_CREATEAPPLICATION_MODAL_CONTROLLER } from './modal/createApplication.modal.controller';
import { CORE_APPLICATION_MODAL_PLATFORMHEALTHOVERRIDE_DIRECTIVE } from './modal/platformHealthOverride.directive';
import { CORE_APPLICATION_CONFIG_APPCONFIG_DATASOURCE } from './config/appConfig.dataSource';

export const APPLICATION_MODULE = 'spinnaker.core.application';
module(APPLICATION_MODULE, [
  APPLICATION_STATE_PROVIDER,
  APPLICATIONS_STATE_PROVIDER,
  CORE_APPLICATION_CONFIG_APPLICATIONCONFIG_CONTROLLER,
  CORE_APPLICATION_MODAL_CREATEAPPLICATION_MODAL_CONTROLLER,
  CORE_APPLICATION_MODAL_PLATFORMHEALTHOVERRIDE_DIRECTIVE,
  CORE_APPLICATION_CONFIG_APPCONFIG_DATASOURCE,
  PERMISSIONS_CONFIGURER_COMPONENT,
]);
