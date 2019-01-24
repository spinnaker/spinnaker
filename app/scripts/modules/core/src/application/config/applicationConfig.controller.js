import { APPLICATION_DATA_SOURCE_EDITOR } from './dataSources/applicationDataSourceEditor.component';
import { CHAOS_MONKEY_CONFIG_COMPONENT } from 'core/chaosMonkey/chaosMonkeyConfig.component';
import { TRAFFIC_GUARD_CONFIG_COMPONENT } from './trafficGuard/trafficGuardConfig.component';
import { SETTINGS } from 'core/config/settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.controller', [
    require('@uirouter/angularjs').default,
    require('./applicationAttributes.directive').name,
    require('./applicationNotifications.directive').name,
    require('./applicationCacheManagement.directive').name,
    require('./deleteApplicationSection.directive').name,
    require('./applicationSnapshotSection.component').name,
    APPLICATION_DATA_SOURCE_EDITOR,
    CHAOS_MONKEY_CONFIG_COMPONENT,
    TRAFFIC_GUARD_CONFIG_COMPONENT,
    require('./links/applicationLinks.component').name,
  ])
  .controller('ApplicationConfigController', function($state, app) {
    this.application = app;
    this.isDataSourceEnabled = key => app.dataSources.some(ds => ds.key === key && ds.disabled === false);
    this.feature = SETTINGS.feature;
    if (app.notFound) {
      $state.go('home.infrastructure', null, { location: 'replace' });
    } else {
      this.application.attributes.instancePort =
        this.application.attributes.instancePort || SETTINGS.defaultInstancePort || null;
    }
  });
