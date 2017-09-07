import { module } from 'angular';

import './ApplicationSearchResultFormatter';
import { APPLICATION_NAV_COMPONENT } from './nav/applicationNav.component';
import { APPLICATION_NAV_SECONDARY_COMPONENT } from './nav/applicationNavSecondary.component';
import { APPLICATION_STATE_PROVIDER } from './application.state.provider';
import { APPLICATIONS_COMPONENT } from './applications.component';
import { APPLICATIONS_STATE_PROVIDER } from './applications.state.provider';
import { PERMISSIONS_CONFIGURER_COMPONENT } from './modal/permissionsConfigurer.component';
import { UPSERT_APPLICATION_HELP } from './modal/upsertApplication.help';

export const APPLICATION_MODULE = 'spinnaker.core.application';
module(APPLICATION_MODULE, [
    APPLICATION_STATE_PROVIDER,
    APPLICATIONS_STATE_PROVIDER,
    APPLICATIONS_COMPONENT,
    require('./config/applicationConfig.controller.js'),
    require('./modal/createApplication.modal.controller.js'),
    require('./modal/pageApplicationOwner.modal.controller.js'),
    require('./modal/platformHealthOverride.directive'),
    require('./config/appConfig.dataSource'),
    APPLICATION_NAV_COMPONENT,
    APPLICATION_NAV_SECONDARY_COMPONENT,
    PERMISSIONS_CONFIGURER_COMPONENT,
    UPSERT_APPLICATION_HELP,
  ]);
