'use strict';

import { module } from 'angular';
import _ from 'lodash';

import { CollapsibleSectionStateCache } from '../../../cache';
import { HEALTH_COUNTS_COMPONENT } from '../../../healthCounts/healthCounts.component';
import { UrlBuilder } from '../../../navigation';
import { CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_SERVICE } from '../regionFilter/regionFilter.service';
import { ClusterState } from '../../../state';
import { TIME_FORMATTERS } from '../../../utils/timeFormatters';

import './projectCluster.less';

export const CORE_PROJECTS_DASHBOARD_CLUSTER_PROJECTCLUSTER_DIRECTIVE =
  'spinnaker.core.projects.dashboard.clusters.projectCluster.directive';
export const name = CORE_PROJECTS_DASHBOARD_CLUSTER_PROJECTCLUSTER_DIRECTIVE; // for backwards compatibility
module(CORE_PROJECTS_DASHBOARD_CLUSTER_PROJECTCLUSTER_DIRECTIVE, [
  TIME_FORMATTERS,
  HEALTH_COUNTS_COMPONENT,
  CORE_PROJECTS_DASHBOARD_REGIONFILTER_REGIONFILTER_SERVICE,
])
  .directive('projectCluster', function () {
    return {
      restrict: 'E',
      templateUrl: require('./projectCluster.directive.html'),
      scope: {},
      bindToController: {
        project: '=',
        cluster: '=',
      },
      controller: 'ProjectClusterCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ProjectClusterCtrl', [
    '$scope',
    'regionFilterService',
    function ($scope, regionFilterService) {
      const stateCache = CollapsibleSectionStateCache;

      const getCacheKey = () => [this.project.name, this.cluster.account, this.cluster.stack].join(':');

      this.clearFilters = (r) => ClusterState.filterService.overrideFiltersForUrl(r);

      this.refreshTooltipTemplate = require('./projectClusterRefresh.tooltip.html');
      this.inconsistentBuildsTemplate = require('./inconsistentBuilds.tooltip.html');

      this.toggle = () => {
        this.state.expanded = !this.state.expanded;
        stateCache.setExpanded(getCacheKey(), this.state.expanded);
      };

      const getMetadata = (application) => {
        const stack = this.cluster.stack;
        const detail = this.cluster.detail;
        const clusterParam = !stack && !detail ? application.name : null;
        const stackParam = stack && stack !== '*' ? stack : null;
        const detailParam = detail && detail !== '*' ? detail : null;

        return {
          type: 'clusters',
          project: this.project.name,
          application: application.application,
          cluster: clusterParam,
          stack: stackParam,
          detail: detailParam,
          account: this.cluster.account,
        };
      };

      const addMetadata = (application) => {
        const baseMetadata = getMetadata(application);
        application.metadata = baseMetadata;
        application.metadata.href = UrlBuilder.buildFromMetadata(baseMetadata);
        application.clusters.forEach((cluster) => {
          const clusterMetadata = getMetadata(application);
          clusterMetadata.region = cluster.region;
          clusterMetadata.href = UrlBuilder.buildFromMetadata(clusterMetadata);
          cluster.metadata = clusterMetadata;
        });
      };

      const getBuildUrl = (build) => [build.host + 'job', build.job, build.buildNumber, ''].join('/');

      const addApplicationBuild = (application) => {
        const allBuilds = _.chain((application.clusters || []).map((cluster) => cluster.builds))
          .flatten()
          .compact()
          .uniqBy((build) => build.buildNumber)
          .value();
        if (allBuilds.length) {
          application.build = _.maxBy(allBuilds, (build) => Number(build.buildNumber));
          application.build.url = getBuildUrl(application.build);
          application.hasInconsistentBuilds = allBuilds.length > 1;
        }
      };

      const applyInconsistentBuildFlag = (application) => {
        application.clusters.forEach((cluster) => {
          const builds = cluster.builds || [];
          if (builds.length && (builds.length > 1 || builds[0].buildNumber !== application.build.buildNumber)) {
            application.hasInconsistentBuilds = true;
            cluster.inconsistentBuilds = cluster.builds.filter(
              (build) => build.buildNumber !== application.build.buildNumber,
            );
          }
        });
      };

      const mapClustersToRegions = (cluster, application) => {
        application.regions = {};
        cluster.regions.forEach((region) => {
          application.regions[region] = _.find(
            application.clusters,
            (regionCluster) => regionCluster.region === region,
          );
        });
      };

      const addRegions = (cluster) => {
        cluster.regions = _.uniq(
          _.flatten(
            cluster.applications.map((application) =>
              application.clusters.map((regionCluster) => regionCluster.region),
            ),
          ),
        ).sort();
      };

      const setViewRegions = (updatedFilter) => {
        const unfilteredRegions = this.cluster.regions;
        if (Object.keys(_.filter(updatedFilter)).length) {
          this.regions = unfilteredRegions.filter((region) => updatedFilter[region]);
        } else {
          this.regions = unfilteredRegions;
        }
      };

      const setViewInstanceCounts = (updatedFilter) => {
        if (Object.keys(_.filter(updatedFilter)).length) {
          this.instanceCounts = _.chain(this.cluster.applications)
            .map('clusters')
            .flatten()
            .value()
            .reduce((instanceCounts, cluster) => {
              if (updatedFilter[cluster.region]) {
                _.forEach(cluster.instanceCounts, (count, key) => {
                  if (!instanceCounts[key]) {
                    instanceCounts[key] = 0;
                  }
                  instanceCounts[key] += count;
                });
              }
              return instanceCounts;
            }, {});
        } else {
          this.instanceCounts = this.cluster.instanceCounts;
        }
      };

      const initialize = () => {
        this.state = {
          expanded: stateCache.isSet(getCacheKey()) ? stateCache.isExpanded(getCacheKey()) : true,
        };

        [setViewInstanceCounts, setViewRegions].forEach((cb) => {
          regionFilterService.registerCallback(cb);
          $scope.$on('$destroy', () => regionFilterService.deregisterCallback(cb));
        });
        addRegions(this.cluster);
        regionFilterService.runCallbacks();
        this.cluster.applications.forEach((application) => {
          mapClustersToRegions(this.cluster, application);
          addApplicationBuild(application);
          applyInconsistentBuildFlag(application);
          addMetadata(application);
        });
        this.clusterLabel = this.cluster.detail
          ? [this.cluster.stack, this.cluster.detail].join('-')
          : this.cluster.stack;
      };

      this.$onInit = () => initialize();
    },
  ]);
