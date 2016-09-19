import {DataSourceConfig} from '../application/service/applicationDataSource.ts';
import dataSourceRegistryModule from '../application/service/applicationDataSource.registry.ts';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.loadBalancer.dataSource', [
    dataSourceRegistryModule,
    require('./loadBalancer.read.service'),
  ])
  .run(function($q, applicationDataSourceRegistry, loadBalancerReader) {

    let loadLoadBalancers = (application) => {
      return loadBalancerReader.loadLoadBalancers(application.name);
    };

    let addLoadBalancers = (application, loadBalancers) => {
      return $q.when(loadBalancers);
    };

    applicationDataSourceRegistry.registerDataSource(new DataSourceConfig({
      key: 'loadBalancers',
      optional: true,
      loader: loadLoadBalancers,
      onLoad: addLoadBalancers,
      providerField: 'type',
      credentialsField: 'account',
      regionField: 'region',
    }));
  });
