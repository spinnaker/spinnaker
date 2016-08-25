'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.loadBalancer.dependentFilterHelper.service', [
    require('../../utils/lodash.js')
  ])
  .factory('loadBalancerDependentFilterHelper', function (_) {
    let poolValueCoordinates = [
      { filterField: 'providerType', on: 'loadBalancer', localField: 'type' },
      { filterField: 'account', on: 'loadBalancer', localField: 'account' },
      { filterField: 'region', on: 'loadBalancer', localField: 'region' },
      { filterField: 'availabilityZone', on: 'instance', localField: 'zone' }
    ];

    function poolBuilder (loadBalancers) {
      let pool = _(loadBalancers)
        .map((lb) => {

          let poolUnitTemplate = _(poolValueCoordinates)
            .filter({ on: 'loadBalancer' })
            .reduce((poolUnitTemplate, coordinate) => {
              poolUnitTemplate[coordinate.filterField] = lb[coordinate.localField];
              return poolUnitTemplate;
            }, {})
            .valueOf();

          let poolUnits = _(['instances', 'detachedInstances'])
            .map((instanceStatus) => lb[instanceStatus])
            .flatten()
            .map((instance) => {
              let poolUnit = _.cloneDeep(poolUnitTemplate);
              return _(poolValueCoordinates)
                .filter({ on: 'instance' })
                .reduce((poolUnit, coordinate) => {
                  poolUnit[coordinate.filterField] = instance[coordinate.localField];
                  return poolUnit;
                }, poolUnit)
                .valueOf();
            })
            .valueOf();

          if (!poolUnits.length) {
            poolUnits.push(poolUnitTemplate);
          }

          return poolUnits;
        })
        .flatten()
        .valueOf();

      return pool;
    }

    return { poolBuilder };
  });
