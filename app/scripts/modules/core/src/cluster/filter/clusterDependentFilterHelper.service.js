const poolValueCoordinates = [
  { filterField: 'providerType', on: 'serverGroup', localField: 'type' },
  { filterField: 'account', on: 'serverGroup', localField: 'account' },
  { filterField: 'region', on: 'serverGroup', localField: 'region' },
  { filterField: 'availabilityZone', on: 'instance', localField: 'availabilityZone' },
  { filterField: 'instanceType', on: 'serverGroup', localField: 'instanceType' },
];

export const poolBuilder = (serverGroups) => {
  const pool = _.chain(serverGroups)
    .map((sg) => {
      const poolUnitTemplate = _.chain(poolValueCoordinates)
        .filter({ on: 'serverGroup' })
        .reduce((poolUnitTemplate, coordinate) => {
          poolUnitTemplate[coordinate.filterField] = sg[coordinate.localField];
          return poolUnitTemplate;
        }, {})
        .value();

      const poolUnits = sg.instances.map((instance) => {
        const poolUnit = _.cloneDeep(poolUnitTemplate);
        return _.chain(poolValueCoordinates)
          .filter({ on: 'instance' })
          .reduce((poolUnit, coordinate) => {
            poolUnit[coordinate.filterField] = instance[coordinate.localField];
            return poolUnit;
          }, poolUnit)
          .value();
      });

      if (!poolUnits.length) {
        poolUnits.push(poolUnitTemplate);
      }

      return poolUnits;
    })
    .flatten()
    .value();

  return pool;
};
