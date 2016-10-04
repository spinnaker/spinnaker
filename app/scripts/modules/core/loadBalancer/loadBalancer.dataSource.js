import {DataSourceConfig} from '../application/service/applicationDataSource';
import dataSourceRegistryModule from '../application/service/applicationDataSource.registry';

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
      description: 'Traffic distribution management between servers'
    }));
  });
