'use strict';

let angular = require('angular');

require('./projectCluster.less');

module.exports = angular.module('spinnaker.core.projects.dashboard.clusters.projectCluster.directive', [
  require('../../../account/collapsibleAccountTag.directive.js'),
  require('../../../navigation/urlBuilder.service.js'),
  require('../../../utils/lodash.js'),
  require('../../../cache/collapsibleSectionStateCache.js'),
  require('../../../scheduler/scheduler.service.js'),
  require('../../../cluster/filter/clusterFilter.service.js'),
  require('../../../utils/timeFormatters.js'),
  require('../../../healthCounts/healthCounts.directive.js'),
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
  .controller('ProjectClusterCtrl', function($scope, urlBuilderService, _, collapsibleSectionStateCache, scheduler,
                                             clusterFilterService) {

    let stateCache = collapsibleSectionStateCache;

    let getCacheKey = () => [this.project.name, this.cluster.account, this.cluster.stack].join(':');

    this.clearFilters = clusterFilterService.overrideFiltersForUrl;

    this.refreshTooltipTemplate = require('./projectClusterRefresh.tooltip.html');
    this.inconsistentBuildsTemplate = require('./inconsistentBuilds.tooltip.html');

    this.state = {
      expanded: stateCache.isSet(getCacheKey()) ? stateCache.isExpanded(getCacheKey()) : true,
    };

    this.toggle = () => {
      this.state.expanded = !this.state.expanded;
      stateCache.setExpanded(getCacheKey(), this.state.expanded);
    };

    let getMetadata = (application) => {
      let stack = this.cluster.stack,
          detail = this.cluster.detail,
          clusterParam = !stack && !detail ? application.name : null,
          stackParam = stack && stack !== '*' ? stack : null,
          detailParam = detail && detail !== '*' ? detail : null;

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

    let addMetadata = (application) => {
      let baseMetadata = getMetadata(application);
      application.metadata = baseMetadata;
      application.metadata.href = urlBuilderService.buildFromMetadata(baseMetadata);
      application.clusters.forEach((cluster) => {
        let clusterMetadata = getMetadata(application);
        clusterMetadata.region = cluster.region;
        clusterMetadata.href = urlBuilderService.buildFromMetadata(clusterMetadata);
        cluster.metadata = clusterMetadata;
      });
    };

    let getBuildUrl = (build) => [build.host + 'job', build.job, build.buildNumber, ''].join('/');

    let addApplicationBuild = (application) => {
      let allBuilds = _((application.clusters || []).map((cluster) => cluster.builds))
        .flatten()
        .compact()
        .uniq((build) => build.buildNumber)
        .value();
      if (allBuilds.length) {
        application.build = _.max(allBuilds, (build) => Number(build.buildNumber));
        application.build.url = getBuildUrl(application.build);
        application.hasInconsistentBuilds = allBuilds.length > 1;
      }
    };

    let applyInconsistentBuildFlag = (application) => {
      application.clusters.forEach((cluster) => {
        let builds = cluster.builds || [];
        if (builds.length && (builds.length > 1 || builds[0].buildNumber !== application.build.buildNumber)) {
          application.hasInconsistentBuilds = true;
          cluster.inconsistentBuilds = cluster.builds.filter(
            (build) => build.buildNumber !== application.build.buildNumber);
        }
      });
    };

    let mapClustersToRegions = (cluster, application) => {
      application.regions = {};
      cluster.regions.forEach((region) => {
        application.regions[region] = _.find(application.clusters, (regionCluster) => regionCluster.region === region);
      });
    };

    let addRegions = (cluster) => {
      cluster.regions = _.uniq(_.flatten(
        cluster.applications.map((application) =>
         application.clusters.map((regionCluster) => regionCluster.region)
        )
      )).sort();
    };

    let initialize = () => {
      addRegions(this.cluster);
      this.cluster.applications.forEach((application) => {
        mapClustersToRegions(this.cluster, application);
        addApplicationBuild(application);
        applyInconsistentBuildFlag(application);
        addMetadata(application);
      });
    };

    initialize();

    this.clusterLabel = this.cluster.detail ? [this.cluster.stack, this.cluster.detail].join('-') : this.cluster.stack;

  });
