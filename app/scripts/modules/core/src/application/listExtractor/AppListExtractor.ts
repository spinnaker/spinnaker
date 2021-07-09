import { compact, flatten, uniq } from 'lodash';

import { Application } from '../application.model';
import { IInstance, IServerGroup } from '../../domain';
import { IMoniker } from '../../naming/IMoniker';

export type IServerGroupFilter = (s: IServerGroup) => boolean;

export type IInstanceFilter = (i: IInstance) => boolean;

const defaultFilter = () => true;

export class AppListExtractor {
  public static getMonikers(applications: Application[], filter: IServerGroupFilter = defaultFilter): IMoniker[] {
    const allMonikers: IMoniker[][] = applications.map((a) =>
      a
        .getDataSource('serverGroups')
        .data.filter(filter)
        .map((serverGroup: IServerGroup) => serverGroup.moniker),
    );
    return compact(flatten(allMonikers));
  }

  public static getRegions(applications: Application[], filter: IServerGroupFilter = defaultFilter): string[] {
    const allRegions: string[][] = applications.map((a) =>
      a
        .getDataSource('serverGroups')
        .data.filter(filter)
        .map((serverGroup: IServerGroup) => serverGroup.region),
    );
    return uniq(compact(flatten(allRegions))).sort();
  }

  public static getStacks(applications: Application[], filter: IServerGroupFilter = defaultFilter): string[] {
    const allStacks: string[][] = applications.map((a) =>
      a
        .getDataSource('serverGroups')
        .data.filter(filter)
        .map((serverGroup: IServerGroup) => serverGroup.stack),
    );
    return uniq(compact(flatten(allStacks))).sort();
  }

  public static getClusters(applications: Application[], filter: IServerGroupFilter = defaultFilter): string[] {
    const allClusters: string[][] = applications.map((a) =>
      a
        .getDataSource('serverGroups')
        .data.filter(filter)
        .map((serverGroup: IServerGroup) => serverGroup.cluster),
    );
    return uniq(compact(flatten(allClusters))).sort();
  }

  public static getAsgs(applications: Application[], clusterFilter: IServerGroupFilter = defaultFilter): string[] {
    const allNames: string[][] = applications.map((a) =>
      a
        .getDataSource('serverGroups')
        .data.filter(clusterFilter)
        .map((s: IServerGroup) => s.name),
    );
    return uniq(compact(flatten(allNames))).sort();
  }

  public static getZones(
    applications: Application[],
    clusterFilter: IServerGroupFilter = defaultFilter,
    regionFilter: IServerGroupFilter = defaultFilter,
    nameFilter: IServerGroupFilter = defaultFilter,
  ): string[] {
    const allInstances: IInstance[][][] = applications.map((a) =>
      a
        .getDataSource('serverGroups')
        .data.filter(clusterFilter)
        .filter(regionFilter)
        .filter(nameFilter)
        .map((serverGroup: IServerGroup) => serverGroup.instances),
    );

    const instanceZones: string[] = flatten(flatten(allInstances)).map((i: IInstance) => i.availabilityZone);

    return uniq(compact(instanceZones)).sort();
  }

  public static getInstances(
    applications: Application[],
    clusterFilter: IServerGroupFilter = defaultFilter,
    serverGroupFilter: IServerGroupFilter = defaultFilter,
    instanceFilter: IInstanceFilter = defaultFilter,
  ): IInstance[] {
    const allInstances: IInstance[][][] = applications.map((a) =>
      a
        .getDataSource('serverGroups')
        .data.filter(clusterFilter)
        .filter(serverGroupFilter)
        .map((serverGroup: IServerGroup) => serverGroup.instances),
    );

    return uniq(compact(flatten(flatten(allInstances)).filter(instanceFilter)));
  }

  // filter builders

  public static clusterFilterForCredentials(credentials: string): IServerGroupFilter {
    return (serverGroup: IServerGroup) => {
      return credentials ? serverGroup.account === credentials : true;
    };
  }

  public static monikerClusterNameFilter(clusterName: string): IServerGroupFilter {
    return (serverGroup: IServerGroup) => {
      return serverGroup.moniker.cluster === clusterName;
    };
  }

  public static clusterFilterForCredentialsAndRegion(
    credentials: string,
    region: string | string[],
  ): IServerGroupFilter {
    return (serverGroup: IServerGroup) => {
      const accountMatches = credentials ? serverGroup.account === credentials : true;

      const regionMatches =
        serverGroup && Array.isArray(region) && region.length
          ? region.includes(serverGroup.region)
          : region
          ? serverGroup.region === region
          : true;

      return accountMatches && regionMatches;
    };
  }
}
