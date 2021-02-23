import { module } from 'angular';

import { APPLICATION_STATE_PROVIDER } from './application.state.provider';
import './applicationSearchResultType';
import { APPLICATIONS_STATE_PROVIDER } from './applications.state.provider';
import { CORE_APPLICATION_CONFIG_APPCONFIG_DATASOURCE } from './config/appConfig.dataSource';
import { CORE_APPLICATION_CONFIG_APPLICATIONCONFIG_CONTROLLER } from './config/applicationConfig.controller';
import { CORE_APPLICATION_MODAL_CREATEAPPLICATION_MODAL_CONTROLLER } from './modal/createApplication.modal.controller';
import { PERMISSIONS_CONFIGURER_COMPONENT } from './modal/permissionsConfigurer.component';
import { CORE_APPLICATION_MODAL_PLATFORMHEALTHOVERRIDE_DIRECTIVE } from './modal/platformHealthOverride.directive';
import './modal/upsertApplication.help';
import './nav/defaultCategories';

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
