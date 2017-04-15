import {module} from 'angular';

import {DataSourceConfig} from 'core/application/service/applicationDataSource';
import {
  APPLICATION_DATA_SOURCE_REGISTRY,
  ApplicationDataSourceRegistry
} from 'core/application/service/applicationDataSource.registry';
import {NetflixSettings} from '../netflix.settings';

export const TABLEAU_DATASOURCE = 'spinnaker.netflix.tableau.dataSource';
module(TABLEAU_DATASOURCE, [APPLICATION_DATA_SOURCE_REGISTRY])
  .run((applicationDataSourceRegistry: ApplicationDataSourceRegistry) => {
    if (NetflixSettings.feature.tableau) {
      applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
        key: 'analytics',
        sref: '.analytics',
        optIn: true,
        optional: true,
        description: 'Pipeline and task statistics'
      }));
    }
  });
