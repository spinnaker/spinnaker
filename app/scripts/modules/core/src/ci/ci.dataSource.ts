import { IQService, module } from 'angular';
import { isEmpty } from 'lodash';

import { CIReader } from './CIReader';
import { Application, ApplicationDataSourceRegistry, navigationCategoryRegistry } from '../application';
import { SETTINGS } from '../config/settings';
import { ICiBuild } from './domain';

export const CI_DATASOURCE = 'spinnaker.ci.dataSource';
export const name = CI_DATASOURCE;
module(CI_DATASOURCE, []).run([
  '$q',
  ($q: IQService) => {
    if (!SETTINGS.feature.ci) {
      return;
    }

    const loadBuilds = (application: Application) => {
      const { repoType, repoProjectKey, repoSlug } = application.attributes;
      if (isEmpty(repoProjectKey) || isEmpty(repoType) || isEmpty(repoSlug)) {
        return $q.when([]);
      }
      return CIReader.getBuilds(repoType, repoProjectKey, repoSlug);
    };

    const loadRunningBuilds = (application: Application) => {
      const { repoType, repoProjectKey, repoSlug } = application.attributes;
      if (isEmpty(repoProjectKey) || isEmpty(repoType) || isEmpty(repoSlug)) {
        return $q.when([]);
      }
      return CIReader.getRunningBuilds(repoType, repoProjectKey, repoSlug);
    };

    navigationCategoryRegistry.register({
      key: 'integration',
      label: 'Integration',
      icon: 'icon-ci-branch',
      iconName: 'spCIBranch',
      primary: true,
      order: 0,
    });

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'integration',
      badge: 'runningBuilds',
      icon: 'icon-ci-branch',
      iconName: 'spCIBranch',
      category: 'integration',
      label: 'Integration',
      optional: true,
      lazy: true,
      description: 'Visibility into builds for pull requests and branches',
      requireConfiguredApp: true,
      defaultData: [],
    });

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'builds',
      sref: '.builds',
      badge: 'runningBuilds',
      icon: 'icon-ci-build',
      iconName: 'spCIBuild',
      category: 'integration',
      label: 'Builds',
      lazy: true,
      requireConfiguredApp: true,
      requiresDataSource: 'integration',
      loader: loadBuilds,
      onLoad: (_: Application, data: ICiBuild) => $q.when(data),
      defaultData: [],
    });

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'runningBuilds',
      visible: false,
      description: 'running builds',
      loader: loadRunningBuilds,
      onLoad: (_: Application, data: ICiBuild) => $q.when(data),
      defaultData: [],
    });
  },
]);
