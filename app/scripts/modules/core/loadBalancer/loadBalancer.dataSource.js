import {DataSourceConfig} from '../application/service/applicationDataSource';
import {APPLICATION_DATA_SOURCE_REGISTRY} from '../application/service/applicationDataSource.registry';
import {ENTITY_TAGS_READ_SERVICE} from '../entityTag/entityTags.read.service';

let angular = require('angular');

module.exports = angular
  .module('spinnaker.core.loadBalancer.dataSource', [
    APPLICATION_DATA_SOURCE_REGISTRY,
    ENTITY_TAGS_READ_SERVICE,
    require('./loadBalancer.read.service'),
    require('core/config/settings'),
  ])
  .run(function($q, applicationDataSourceRegistry, loadBalancerReader, entityTagsReader, settings) {

    let loadLoadBalancers = (application) => {
      return loadBalancerReader.loadLoadBalancers(application.name);
    };

    let addLoadBalancers = (application, loadBalancers) => {
      return addTags(loadBalancers);
    };

    let addTags = (loadBalancers) => {
      if (!settings.feature.entityTags) {
        return $q.when(loadBalancers);
      }
      const entityIds = loadBalancers.map(lb => lb.name);
      return entityTagsReader.getAllEntityTags('loadBalancer', entityIds).then(tags => {
        loadBalancers.forEach(loadBalancer => {
          loadBalancer.entityTags = tags.find(t => t.entityRef.entityId === loadBalancer.name &&
          t.entityRef.account === loadBalancer.account &&
          t.entityRef.region === loadBalancer.region);
        });
        return loadBalancers;
      });
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
