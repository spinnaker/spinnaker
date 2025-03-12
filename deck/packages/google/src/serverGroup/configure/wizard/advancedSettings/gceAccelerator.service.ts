import { get, intersectionBy, isFinite } from 'lodash';

export interface IGceAcceleratorTypeRaw {
  name: string;
  description: string;
  maximumCardsPerInstance: number;
}

export interface IGceAcceleratorType extends IGceAcceleratorTypeRaw {
  availableCardCounts: number[];
}

interface IZoneToAcceleratorTypesMap {
  [zone: string]: IGceAcceleratorTypeRaw[];
}

export interface IGceAcceleratorCommand {
  backingData: any;
  credentials: string;
  distributionPolicy: { zones: string[]; targetShape?: string };
  regional: boolean;
  selectZones: boolean;
  zone: string;
}

export class GceAcceleratorService {
  public static getAvailableAccelerators({
    backingData,
    credentials,
    distributionPolicy,
    regional,
    selectZones,
    zone,
  }: IGceAcceleratorCommand): IGceAcceleratorType[] {
    const acceleratorMap = this.getZoneToAcceleratorTypesMap(backingData, credentials);

    if (zone && !regional) {
      return this.getAcceleratorTypesForZone(acceleratorMap, zone);
    }

    if (regional && selectZones) {
      const zones = get(distributionPolicy, 'zones', []);
      const allZonesAcceleratorTypes = zones.map((z) => this.getAcceleratorTypesForZone(acceleratorMap, z));
      return intersectionBy.apply(null, [...allZonesAcceleratorTypes, 'name']);
    }

    return [];
  }

  private static getZoneToAcceleratorTypesMap(backingData: any, credentials: string): IZoneToAcceleratorTypesMap {
    return get(backingData, ['credentialsKeyedByAccount', credentials, 'zoneToAcceleratorTypesMap'], {});
  }

  private static getAcceleratorTypesForZone(
    acceleratorMap: IZoneToAcceleratorTypesMap,
    zone: string,
  ): IGceAcceleratorType[] {
    return get(acceleratorMap, [zone, 'acceleratorTypes', 'acceleratorTypes'], []).map((a) => {
      return {
        ...a,
        availableCardCounts: this.getAvailableCardCounts(a),
      };
    });
  }

  private static getAvailableCardCounts(accelerator: IGceAcceleratorTypeRaw): number[] {
    const maxCards = isFinite(accelerator.maximumCardsPerInstance) ? accelerator.maximumCardsPerInstance : 4;
    const availableCardCounts = [];
    for (let i = 1; i <= maxCards; i *= 2) {
      availableCardCounts.push(i);
    }
    return availableCardCounts;
  }
}
