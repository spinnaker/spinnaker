import { module } from 'angular';
import { Application } from '../application/application.model';
import { INFRASTRUCTURE_KEY } from '../application/nav/defaultCategories';
import { ApplicationDataSourceRegistry } from '../application/service/ApplicationDataSourceRegistry';
import { ISecurityGroup } from '../domain';
import { EntityTagsReader } from '../entityTag/EntityTagsReader';
import { addManagedResourceMetadataToSecurityGroups } from '../managed';

import { SECURITY_GROUP_READER, SecurityGroupReader } from './securityGroupReader.service';

export const SECURITY_GROUP_DATA_SOURCE = 'spinnaker.core.securityGroup.dataSource';
module(SECURITY_GROUP_DATA_SOURCE, [SECURITY_GROUP_READER]).run([
  'securityGroupReader',
  (securityGroupReader: SecurityGroupReader) => {
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

    ApplicationDataSourceRegistry.registerDataSource({
      key: 'securityGroups',
      label: 'Firewalls',
      category: INFRASTRUCTURE_KEY,
      sref: '.insight.firewalls',
      optional: true,
      icon: 'fa fa-xs fa-fw fa-lock',
      iconName: 'spMenuSecurityGroups',
      loader: loadSecurityGroups,
      onLoad: addSecurityGroups,
      afterLoad: addTags,
      providerField: 'provider',
      credentialsField: 'accountName',
      regionField: 'region',
      description: 'Network traffic access management',
      defaultData: [],
    });
  },
]);
