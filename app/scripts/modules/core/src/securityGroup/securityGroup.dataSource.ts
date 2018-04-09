import { module } from 'angular';

import {
  APPLICATION_DATA_SOURCE_REGISTRY,
  ApplicationDataSourceRegistry,
} from 'core/application/service/applicationDataSource.registry';
import { INFRASTRUCTURE_KEY } from 'core/application/nav/defaultCategories';
import { Application } from 'core/application/application.model';
import { SECURITY_GROUP_READER, SecurityGroupReader } from 'core/securityGroup/securityGroupReader.service';
import { ENTITY_TAGS_READ_SERVICE, EntityTagsReader } from 'core/entityTag/entityTags.read.service';
import { ISecurityGroup } from 'core/domain';

export const SECURITY_GROUP_DATA_SOURCE = 'spinnaker.core.securityGroup.dataSource';
module(SECURITY_GROUP_DATA_SOURCE, [
  APPLICATION_DATA_SOURCE_REGISTRY,
  ENTITY_TAGS_READ_SERVICE,
  SECURITY_GROUP_READER,
]).run(
  (
    applicationDataSourceRegistry: ApplicationDataSourceRegistry,
    securityGroupReader: SecurityGroupReader,
    entityTagsReader: EntityTagsReader,
  ) => {
    const loadSecurityGroups = (application: Application) => {
      return securityGroupReader.loadSecurityGroupsByApplicationName(application.name);
    };

    const addSecurityGroups = (application: Application, securityGroups: ISecurityGroup[]) => {
      return securityGroupReader.getApplicationSecurityGroups(application, securityGroups);
    };

    const addTags = (application: Application) => {
      return entityTagsReader.addTagsToSecurityGroups(application);
    };

    applicationDataSourceRegistry.registerDataSource({
      key: 'securityGroups',
      category: INFRASTRUCTURE_KEY,
      sref: '.insight.securityGroups',
      optional: true,
      icon: 'fa fa-xs fa-fw fa-lock',
      loader: loadSecurityGroups,
      onLoad: addSecurityGroups,
      afterLoad: addTags,
      providerField: 'provider',
      credentialsField: 'accountName',
      regionField: 'region',
      description: 'Network traffic access management',
    });
  },
);
