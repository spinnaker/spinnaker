import UIROUTER_ANGULARJS from '@uirouter/angularjs';
import { module } from 'angular';
import { cloneDeep } from 'lodash';

import { CORE_APPLICATION_CONFIG_APPLICATIONATTRIBUTES_DIRECTIVE } from './applicationAttributes.directive';
import { CORE_APPLICATION_CONFIG_APPLICATIONNOTIFICATIONS_DIRECTIVE } from './applicationNotifications.directive';
import { CORE_APPLICATION_CONFIG_APPLICATIONSNAPSHOTSECTION_COMPONENT } from './applicationSnapshotSection.component';
import { CHAOS_MONKEY_CONFIG_COMPONENT } from '../../chaosMonkey/chaosMonkeyConfig.component';
import { SETTINGS } from '../../config/settings';
import { APPLICATION_DATA_SOURCE_EDITOR } from './dataSources/applicationDataSourceEditor.component';
import { DELETE_APPLICATION_SECTION } from './deleteApplicationSection.module';
import { CORE_APPLICATION_CONFIG_LINKS_APPLICATIONLINKS_COMPONENT } from './links/applicationLinks.component';
import { ApplicationWriter } from '../service/ApplicationWriter';
import { TRAFFIC_GUARD_CONFIG_COMPONENT } from './trafficGuard/trafficGuardConfig.component';

export const CORE_APPLICATION_CONFIG_APPLICATIONCONFIG_CONTROLLER = 'spinnaker.core.application.config.controller';
export const name = CORE_APPLICATION_CONFIG_APPLICATIONCONFIG_CONTROLLER; // for backwards compatibility
module(CORE_APPLICATION_CONFIG_APPLICATIONCONFIG_CONTROLLER, [
  UIROUTER_ANGULARJS,
  CORE_APPLICATION_CONFIG_APPLICATIONATTRIBUTES_DIRECTIVE,
  CORE_APPLICATION_CONFIG_APPLICATIONNOTIFICATIONS_DIRECTIVE,
  CORE_APPLICATION_CONFIG_APPLICATIONSNAPSHOTSECTION_COMPONENT,
  DELETE_APPLICATION_SECTION,
  APPLICATION_DATA_SOURCE_EDITOR,
  CHAOS_MONKEY_CONFIG_COMPONENT,
  TRAFFIC_GUARD_CONFIG_COMPONENT,
  CORE_APPLICATION_CONFIG_LINKS_APPLICATIONLINKS_COMPONENT,
]).controller('ApplicationConfigController', [
  '$state',
  'app',
  '$scope',
  function ($state, app, $scope) {
    this.application = app;
    this.isDataSourceEnabled = (key) => app.dataSources.some((ds) => ds.key === key && ds.disabled === false);
    this.feature = SETTINGS.feature;
    if (app.notFound || app.hasError) {
      $state.go('home.infrastructure', null, { location: 'replace' });
    } else {
      this.application.attributes.instancePort =
        this.application.attributes.instancePort || SETTINGS.defaultInstancePort || null;
    }
    this.bannerConfigProps = {
      isSaving: false,
      saveError: false,
    };
    this.updateBannerConfigs = (bannerConfigs) => {
      const applicationAttributes = cloneDeep(this.application.attributes);
      applicationAttributes.customBanners = bannerConfigs;
      $scope.$applyAsync(() => {
        this.bannerConfigProps.isSaving = true;
        this.bannerConfigProps.saveError = false;
      });
      ApplicationWriter.updateApplication(applicationAttributes)
        .then(() => {
          $scope.$applyAsync(() => {
            this.bannerConfigProps.isSaving = false;
            this.application.attributes = applicationAttributes;
          });
        })
        .catch(() => {
          this.bannerConfigProps.isSaving = false;
          this.bannerConfigProps.saveError = true;
        });
    };

    this.notifications = [];
    this.updateNotifications = (notifications) => {
      $scope.$applyAsync(() => {
        this.notifications = notifications;
      });
    };

    if (this.feature.managedResources) {
      this.hasManagedResources = false;
      this.application
        .getDataSource('managedResources')
        .ready()
        .then(({ hasManagedResources }) => {
          $scope.$applyAsync(() => {
            this.hasManagedResources = hasManagedResources;
          });
        });
    }
  },
]);
