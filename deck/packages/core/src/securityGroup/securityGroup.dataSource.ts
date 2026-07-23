import type { IQService } from 'angular';
import { module } from 'angular';
import { AngularServices } from '../angular/services';
import type { Application } from '../application/application.model';
import { INFRASTRUCTURE_KEY } from '../application/nav/defaultCategories';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import type { ISecurityGroup } from '../domain';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';
import { addManagedResourceMetadataToSecurityGroups } from '../managed';

import type { SecurityGroupReader } from './securityGroupReader.service';
import { SecurityGroupReader as SecurityGroupReaderImpl } from './securityGroupReader.service';
import { SECURITY_GROUP_READER } from './securityGroupReader.service';
import { SecurityGroupTransformerService } from './securityGroupTransformer.service';

export const SECURITY_GROUP_DATA_SOURCE = 'spinnaker.core.securityGroup.dataSource';
function createDataSourceConfig(securityGroupReader: SecurityGroupReader) {
  const loadSecurityGroups = (application: Application) => {
    return securityGroupReader.loadSecurityGroupsByApplicationName(application.name);
  };

  const addSecurityGroups = (application: Application, securityGroups: ISecurityGroup[]) => {
    return securityGroupReader.getApplicationSecurityGroups(application, securityGroups);
  };

  const addTags = (application: Application) => {
    EntityTagsReader.addTagsToSecurityGroups(application);
    addManagedResourceMetadataToSecurityGroups(application);
  };

  return {
    key: 'securityGroups',
    label: 'Firewalls',
    category: INFRASTRUCTURE_KEY,
    sref: '.insight.firewalls',
    optional: true,
    icon: 'fa fa-xs fa-fw fa-lock',
    iconName: 'spMenuSecurityGroups' as const,
    loader: loadSecurityGroups,
    onLoad: addSecurityGroups,
    afterLoad: addTags,
    providerField: 'provider',
    credentialsField: 'accountName',
    regionField: 'region',
    description: 'Network traffic access management',
    defaultData: [] as ISecurityGroup[],
  };
}

export function registerSecurityGroupDataSource($q?: IQService, securityGroupReader?: SecurityGroupReader): void {
  if (ApplicationDataSourceRegistry.getDataSources().some((source) => source.key === 'securityGroups')) {
    return;
  }

  if (!securityGroupReader) {
    const securityGroupTransformer = new SecurityGroupTransformerService(AngularServices.providerServiceDelegate);
    const safeSecurityGroupTransformer = {
      normalizeSecurityGroup: (securityGroup: ISecurityGroup) => {
        const provider = securityGroup.provider || securityGroup.type;
        if (provider && AngularServices.providerServiceDelegate.hasDelegate(provider, 'securityGroup.transformer')) {
          return securityGroupTransformer.normalizeSecurityGroup(securityGroup);
        }
        return Promise.resolve(securityGroup);
      },
    } as SecurityGroupTransformerService;

    securityGroupReader = new SecurityGroupReaderImpl(
      console as any,
      $q || AngularServices.$q,
      safeSecurityGroupTransformer,
      AngularServices.providerServiceDelegate,
    );
  }

  ApplicationDataSourceRegistry.registerDataSource(createDataSourceConfig(securityGroupReader));
}

module(SECURITY_GROUP_DATA_SOURCE, [SECURITY_GROUP_READER]).run([
  '$q',
  'securityGroupReader',
  ($q: IQService, securityGroupReader: SecurityGroupReader) => registerSecurityGroupDataSource($q, securityGroupReader),
]);
