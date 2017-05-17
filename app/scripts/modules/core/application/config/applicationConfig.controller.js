import {APPLICATION_DATA_SOURCE_EDITOR} from './dataSources/applicationDataSourceEditor.component';
import {CHAOS_MONKEY_CONFIG_COMPONENT} from 'core/chaosMonkey/chaosMonkeyConfig.component';
import {TRAFFIC_GUARD_CONFIG_COMPONENT} from './trafficGuard/trafficGuardConfig.component';
import {SETTINGS} from 'core/config/settings';

const angular = require('angular');

module.exports = angular
  .module('spinnaker.core.application.config.controller', [
    require('angular-ui-router').default,
    require('./applicationAttributes.directive.js'),
    require('./applicationNotifications.directive.js'),
    require('./applicationCacheManagement.directive.js'),
    require('./deleteApplicationSection.directive.js'),
    require('./applicationSnapshotSection.component.js'),
    APPLICATION_DATA_SOURCE_EDITOR,
    CHAOS_MONKEY_CONFIG_COMPONENT,
    TRAFFIC_GUARD_CONFIG_COMPONENT,
    require('./links/applicationLinks.component.js'),
  ])
  .controller('ApplicationConfigController', function ($state, app) {
    this.application = app;
    this.feature = SETTINGS.feature;
    if (app.notFound) {
      $state.go('home.infrastructure', null, {location: 'replace'});
    } else {
      this.application.attributes.instancePort = this.application.attributes.instancePort || SETTINGS.defaultInstancePort || null;
    }
  });
