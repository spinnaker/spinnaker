import {DataSourceConfig} from '../application/service/applicationDataSource.ts';
import dataSourceRegistryModule from '../application/service/applicationDataSource.registry.ts';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.securityGroup.dataSource', [
    dataSourceRegistryModule,
    require('./securityGroup.read.service'),
  ])
  .run(function($q, applicationDataSourceRegistry, securityGroupReader) {

    let loadSecurityGroups = (application) => {
      return securityGroupReader.loadSecurityGroupsByApplicationName(application.name);
    };

    let addSecurityGroups = (application, securityGroups) => {
      return securityGroupReader.getApplicationSecurityGroups(application, securityGroups);
    };

    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'securityGroups',
      optional: true,
      loader: loadSecurityGroups,
      onLoad: addSecurityGroups,
      providerField: 'provider',
      credentialsField: 'accountName',
      regionField: 'region',
      description: 'Network traffic access management'
    }));
  });
