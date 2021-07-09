import { IQService, module } from 'angular';

import { ManagedReader } from './ManagedReader';
import { Application, DELIVERY_KEY } from '../application';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { SETTINGS } from '../config/settings';
import { IManagedApplicationSummary } from '../domain';
import {
  addManagedResourceMetadataToLoadBalancers,
  addManagedResourceMetadataToSecurityGroups,
  addManagedResourceMetadataToServerGroups,
} from './managedResourceDecorators';
import { noop } from '../utils';

export const MANAGED_RESOURCES_DATA_SOURCE = 'spinnaker.core.managed.dataSource';
module(MANAGED_RESOURCES_DATA_SOURCE, []).run([
  '$q',
  ($q: IQService) => {
    if (!SETTINGS.feature.managedResources) {
      return;
    }
    const loadManagedResources = (application: Application) => {
      return ManagedReader.getApplicationSummary(application.name);
    };

    const addManagedResources = (application: Application, data: IManagedApplicationSummary) => {
      application.isManagementPaused = data.applicationPaused;
      return $q.when(data);
    };

    const addManagedMetadataToResources = (application: Application) => {
      application.serverGroups.ready().then(() => addManagedResourceMetadataToServerGroups(application), noop);
      application.loadBalancers.ready().then(() => addManagedResourceMetadataToLoadBalancers(application), noop);
      application.securityGroups.ready().then(() => addManagedResourceMetadataToSecurityGroups(application), noop);
    };

    const loadEnvironments = (application: Application) => {
      return ManagedReader.getEnvironmentsSummary(application.name);
    };

    const addEnvironments = (
      application: Application,
      data: IManagedApplicationSummary<'resources' | 'artifacts' | 'environments'>,
    ) => {
      application.isManagementPaused = data.applicationPaused;
      return $q.when(data);
    };

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'managedResources',
      visible: false,
      loader: loadManagedResources,
      onLoad: addManagedResources,
      afterLoad: addManagedMetadataToResources,
      defaultData: { applicationPaused: false, hasManagedResources: false, resources: [] },
    });

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'environments',
      sref: '.environments',
      category: DELIVERY_KEY,
      optional: true,
      optIn: true,
      label: 'Environments',
      icon: 'fa fa-fw fa-xs fa-code-branch',
      iconName: 'spEnvironments',
      description: 'Artifacts and environments managed by Spinnaker',
      loader: loadEnvironments,
      onLoad: addEnvironments,
      defaultData: {
        applicationPaused: false,
        hasManagedResources: false,
        environments: [],
        artifacts: [],
        resources: [],
      },
    });
  },
]);
