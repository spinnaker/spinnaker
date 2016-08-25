'use strict';

let angular = require('angular');

module.exports = angular.module('spinnaker.deck.core.cluster.dependentFilterHelper.service', [
    require('../../utils/lodash.js')
  ])
  .factory('clusterDependentFilterHelper', function (_) {
    let poolValueCoordinates = [
      { filterField: 'providerType', on: 'serverGroup', localField: 'type' },
      { filterField: 'account', on: 'serverGroup', localField: 'account' },
      { filterField: 'region', on: 'serverGroup', localField: 'region' },
      { filterField: 'availabilityZone', on: 'instance', localField: 'availabilityZone' },
      { filterField: 'instanceType', on: 'serverGroup', localField: 'instanceType' }
    ];

    function poolBuilder (serverGroups) {
      let pool = _(serverGroups)
        .map((sg) => {

          let poolUnitTemplate = _(poolValueCoordinates)
            .filter({ on: 'serverGroup' })
            .reduce((poolUnitTemplate, coordinate) => {
              poolUnitTemplate[coordinate.filterField] = sg[coordinate.localField];
              return poolUnitTemplate;
            }, {})
            .valueOf();

          let poolUnits = sg.instances.map((instance) => {
            let poolUnit = _.cloneDeep(poolUnitTemplate);
            return _(poolValueCoordinates)
              .filter({ on: 'instance' })
              .reduce((poolUnit, coordinate) => {
                poolUnit[coordinate.filterField] = instance[coordinate.localField];
                return poolUnit;
              }, poolUnit)
              .valueOf();
          });

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
