import { uniq } from 'lodash';

import type { IInstanceCounts, IProject } from '../../domain';
import type { IUrlBuilderInput } from '../../navigation';
import { UrlBuilder } from '../../navigation';

export interface IProjectBuild {
  buildNumber: string | number;
  host?: string;
  images?: string[];
  job?: string;
  url?: string;
}

export interface IProjectRegionCluster {
  builds?: IProjectBuild[];
  inconsistentBuilds?: IProjectBuild[];
  instanceCounts: IInstanceCounts;
  lastPush?: number;
  metadata?: IProjectClusterMetadata;
  region: string;
}

export interface IProjectDashboardApplication {
  application: string;
  build?: IProjectBuild;
  clusters: IProjectRegionCluster[];
  hasInconsistentBuilds?: boolean;
  lastPush?: number;
  metadata?: IProjectClusterMetadata;
  regions?: { [region: string]: IProjectRegionCluster };
}

export interface IProjectDashboardCluster {
  account: string;
  applications: IProjectDashboardApplication[];
  detail?: string;
  instanceCounts: IInstanceCounts;
  regions?: string[];
  stack?: string;
}

export interface IProjectClusterMetadata extends IUrlBuilderInput {
  href?: string;
}

export interface IProjectClusterViewModel {
  applications: IProjectDashboardApplication[];
  clusterLabel: string;
  instanceCounts: IInstanceCounts;
  regions: string[];
}

export type IRegionSelection = { [region: string]: boolean };

const hasSelectedRegions = (selectedRegions: IRegionSelection = {}): boolean =>
  Object.keys(selectedRegions).some((region) => selectedRegions[region]);

export const getProjectClusterRegions = (cluster: IProjectDashboardCluster): string[] => {
  return uniq(
    cluster.applications.reduce((regions: string[], application) => {
      application.clusters.forEach((regionCluster) => regions.push(regionCluster.region));
      return regions;
    }, []),
  ).sort();
};

export const getVisibleRegions = (
  cluster: IProjectDashboardCluster,
  selectedRegions: IRegionSelection = {},
): string[] => {
  const regions = cluster.regions || getProjectClusterRegions(cluster);
  return hasSelectedRegions(selectedRegions) ? regions.filter((region) => selectedRegions[region]) : regions;
};

export const getFilteredInstanceCounts = (
  cluster: IProjectDashboardCluster,
  selectedRegions: IRegionSelection = {},
): IInstanceCounts => {
  if (!hasSelectedRegions(selectedRegions)) {
    return cluster.instanceCounts;
  }

  return cluster.applications.reduce((counts: IInstanceCounts, application) => {
    application.clusters.forEach((regionCluster) => {
      if (selectedRegions[regionCluster.region]) {
        Object.keys(regionCluster.instanceCounts || {}).forEach((key) => {
          counts[key] = (counts[key] || 0) + (regionCluster.instanceCounts[key] || 0);
        });
      }
    });
    return counts;
  }, {} as IInstanceCounts);
};

const getBuildUrl = (build: IProjectBuild): string => {
  return build.host && build.job && build.buildNumber
    ? [build.host + 'job', build.job, build.buildNumber, ''].join('/')
    : null;
};

const getApplicationBuild = (application: IProjectDashboardApplication): IProjectBuild => {
  const buildsByNumber: { [buildNumber: string]: IProjectBuild } = {};
  application.clusters.forEach((cluster) => {
    (cluster.builds || []).forEach((build) => {
      buildsByNumber[String(build.buildNumber)] = build;
    });
  });

  const builds = Object.values(buildsByNumber);
  if (!builds.length) {
    return null;
  }

  const build = builds.reduce((current, next) =>
    Number(next.buildNumber) > Number(current.buildNumber) ? next : current,
  );
  return { ...build, url: getBuildUrl(build) };
};

const addInconsistentBuildFlags = (
  application: IProjectDashboardApplication,
  build: IProjectBuild,
): IProjectDashboardApplication => {
  if (!build) {
    return application;
  }

  let hasInconsistentBuilds = application.clusters.some((cluster) => (cluster.builds || []).length > 1);
  const clusters = application.clusters.map((cluster) => {
    const builds = cluster.builds || [];
    if (builds.length && (builds.length > 1 || String(builds[0].buildNumber) !== String(build.buildNumber))) {
      hasInconsistentBuilds = true;
      return {
        ...cluster,
        inconsistentBuilds: builds.filter(
          (clusterBuild) => String(clusterBuild.buildNumber) !== String(build.buildNumber),
        ),
      };
    }
    return cluster;
  });

  return { ...application, clusters, hasInconsistentBuilds };
};

const getMetadata = (
  project: Pick<IProject, 'name'>,
  cluster: IProjectDashboardCluster,
  application: IProjectDashboardApplication,
): IProjectClusterMetadata => {
  const stack = cluster.stack;
  const detail = cluster.detail;
  const metadata: IProjectClusterMetadata = {
    type: 'clusters',
    project: project.name,
    application: application.application,
    cluster: !stack && !detail ? application.application : null,
    stack: stack && stack !== '*' ? stack : null,
    detail: detail && detail !== '*' ? detail : null,
    account: cluster.account,
  };

  return { ...metadata, href: UrlBuilder.buildFromMetadata(metadata) };
};

const addMetadata = (
  project: Pick<IProject, 'name'>,
  cluster: IProjectDashboardCluster,
  application: IProjectDashboardApplication,
): IProjectDashboardApplication => {
  const metadata = getMetadata(project, cluster, application);
  const regions = application.clusters.reduce((acc, regionCluster) => {
    const clusterMetadata = { ...getMetadata(project, cluster, application), region: regionCluster.region };
    clusterMetadata.href = UrlBuilder.buildFromMetadata(clusterMetadata);
    acc[regionCluster.region] = { ...regionCluster, metadata: clusterMetadata };
    return acc;
  }, {} as { [region: string]: IProjectRegionCluster });

  return { ...application, metadata, regions };
};

export const getProjectClusterViewModel = (
  project: Pick<IProject, 'name'>,
  cluster: IProjectDashboardCluster,
  selectedRegions: IRegionSelection = {},
): IProjectClusterViewModel => {
  const regions = getProjectClusterRegions(cluster);
  const clusterWithRegions = { ...cluster, regions };
  const applications = clusterWithRegions.applications.map((application) => {
    const build = getApplicationBuild(application);
    const withBuildFlags = addInconsistentBuildFlags({ ...application, build }, build);
    return addMetadata(project, clusterWithRegions, withBuildFlags);
  });

  return {
    applications,
    clusterLabel: cluster.detail ? [cluster.stack, cluster.detail].join('-') : cluster.stack,
    instanceCounts: getFilteredInstanceCounts(clusterWithRegions, selectedRegions),
    regions: getVisibleRegions(clusterWithRegions, selectedRegions),
  };
};
