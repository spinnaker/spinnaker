'use strict';

import _ from 'lodash';

const angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.loadBalancer.dependentFilterHelper.service', [])
  .factory('loadBalancerDependentFilterHelper', function () {
    let poolValueCoordinates = [
      { filterField: 'providerType', on: 'loadBalancer', localField: 'type' },
      { filterField: 'account', on: 'loadBalancer', localField: 'account' },
      { filterField: 'region', on: 'loadBalancer', localField: 'region' },
      { filterField: 'availabilityZone', on: 'instance', localField: 'zone' }
    ];

    function poolBuilder (loadBalancers) {
      let pool = _.chain(loadBalancers)
        .map((lb) => {

          let poolUnitTemplate = _.chain(poolValueCoordinates)
            .filter({ on: 'loadBalancer' })
            .reduce((poolUnitTemplate, coordinate) => {
              poolUnitTemplate[coordinate.filterField] = lb[coordinate.localField];
              return poolUnitTemplate;
            }, {})
            .value();

          let poolUnits = _.chain(['instances', 'detachedInstances'])
            .map((instanceStatus) => lb[instanceStatus])
            .flatten()
            .map((instance) => {
              let poolUnit = _.cloneDeep(poolUnitTemplate);
              if (!instance) {
                return poolUnit;
              }

              return _.chain(poolValueCoordinates)
                .filter({ on: 'instance' })
                .reduce((poolUnit, coordinate) => {
                  poolUnit[coordinate.filterField] = instance[coordinate.localField];
                  return poolUnit;
                }, poolUnit)
                .value();
            })
            .value();

          if (!poolUnits.length) {
            poolUnits.push(poolUnitTemplate);
          }

          return poolUnits;
        })
        .flatten()
        .value();

      return pool;
    }

    return { poolBuilder };
  });
