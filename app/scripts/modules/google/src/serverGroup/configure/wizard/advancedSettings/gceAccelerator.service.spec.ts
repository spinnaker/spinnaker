import { GceAcceleratorService, IGceAcceleratorCommand, IGceAcceleratorTypeRaw } from './gceAccelerator.service';

describe('GceAcceleratorService', () => {
  const acceleratorA: IGceAcceleratorTypeRaw = {
    name: 'accelerator-a',
    description: 'Accelerator A',
    maximumCardsPerInstance: 8,
  };
  const acceleratorB: IGceAcceleratorTypeRaw = {
    name: 'accelerator-b',
    description: 'Accelerator B',
    maximumCardsPerInstance: 4,
  };
  const acceleratorC: IGceAcceleratorTypeRaw = {
    name: 'accelerator-c',
    description: 'Accelerator C',
    maximumCardsPerInstance: 1,
  };
  let command: IGceAcceleratorCommand;
  beforeEach(() => {
    command = {
      backingData: {
        credentialsKeyedByAccount: {
          'my-gce-account': {
            zoneToAcceleratorTypesMap: {
              'us-east1-b': {
                acceleratorTypes: {
                  acceleratorTypes: [acceleratorA, acceleratorB],
                },
              },
              'us-east1-c': {
                acceleratorTypes: {
                  acceleratorTypes: [acceleratorB, acceleratorC],
                },
              },
            },
          },
        },
      },
      credentials: 'my-gce-account',
      distributionPolicy: null,
      regional: false,
      selectZones: false,
      zone: 'us-east1-b',
    };
  });
  describe('getAvailableAccelerators', () => {
    it('Returns empty list for regional server group without explicitly selected zones', () => {
      command.regional = true;
      expect(GceAcceleratorService.getAvailableAccelerators(command)).toEqual([]);
      command.selectZones = true;
      command.distributionPolicy = {
        zones: [],
      };
      expect(GceAcceleratorService.getAvailableAccelerators(command)).toEqual([]);
    });
    it('returns intersection of accelerators of explicitly selected zones for regional server group', () => {
      command.regional = true;
      command.selectZones = true;
      command.distributionPolicy = {
        zones: ['us-east1-b', 'us-east1-c'],
      };
      expect(GceAcceleratorService.getAvailableAccelerators(command)).toEqual([
        {
          ...acceleratorB,
          availableCardCounts: [1, 2, 4],
        },
      ]);
      command.distributionPolicy = {
        zones: ['us-east1-b'],
      };
      expect(GceAcceleratorService.getAvailableAccelerators(command)).toEqual([
        {
          ...acceleratorA,
          availableCardCounts: [1, 2, 4, 8],
        },
        {
          ...acceleratorB,
          availableCardCounts: [1, 2, 4],
        },
      ]);
    });
    it('returns accelerators available for selected zone for single-zone server groups', () => {
      expect(GceAcceleratorService.getAvailableAccelerators(command)).toEqual([
        {
          ...acceleratorA,
          availableCardCounts: [1, 2, 4, 8],
        },
        {
          ...acceleratorB,
          availableCardCounts: [1, 2, 4],
        },
      ]);
      command.zone = 'us-east1-c';
      expect(GceAcceleratorService.getAvailableAccelerators(command)).toEqual([
        {
          ...acceleratorB,
          availableCardCounts: [1, 2, 4],
        },
        {
          ...acceleratorC,
          availableCardCounts: [1],
        },
      ]);
    });
  });
});
