import { module } from 'angular';

import './ApplicationSearchResultFormatter';
import { SECONDARY_APPLICATION_NAV_COMPONENT } from './nav/secondaryNav.component';
import { APPLICATION_STATE_PROVIDER } from './application.state.provider';
import { APPLICATIONS_STATE_PROVIDER } from './applications.state.provider';
import { APPLICATION_COMPONENT } from './application.component';
import { PERMISSIONS_CONFIGURER_COMPONENT } from './modal/permissionsConfigurer.component';
import { UPSERT_APPLICATION_HELP } from './modal/upsertApplication.help';

export const APPLICATION_MODULE = 'spinnaker.core.application';
module(APPLICATION_MODULE, [
    APPLICATION_STATE_PROVIDER,
    APPLICATIONS_STATE_PROVIDER,
    APPLICATION_COMPONENT,
    require('./applications.controller.js'),
    require('./config/applicationConfig.controller.js'),
    require('./modal/createApplication.modal.controller.js'),
    require('./modal/pageApplicationOwner.modal.controller.js'),
    require('./modal/platformHealthOverride.directive'),
    require('./inferredApplicationWarning.service.js'),
    require('./config/appConfig.dataSource'),
    require('./nav/applicationNav.component'),
    SECONDARY_APPLICATION_NAV_COMPONENT,
    PERMISSIONS_CONFIGURER_COMPONENT,
    UPSERT_APPLICATION_HELP,
  ]);
