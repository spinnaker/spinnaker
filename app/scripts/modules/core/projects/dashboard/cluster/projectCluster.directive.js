'use strict';

let angular = require('angular');

require('./projectCluster.less');

module.exports = angular.module('spinnaker.core.projects.dashboard.clusters.projectCluster.directive', [
  require('../../../account/collapsibleAccountTag.directive.js'),
  require('../../../navigation/urlBuilder.service.js'),
  require('../../../cluster/cluster.service.js'),
  require('../../../utils/lodash.js'),
  require('../../../cache/collapsibleSectionStateCache.js'),
  require('../../../scheduler/scheduler.service.js'),
  require('../../../naming/naming.service.js'),
  require('../../../cluster/filter/clusterFilter.service.js'),
])
  .directive('projectCluster', function () {
    return {
      restrict: 'E',
      templateUrl: require('./projectCluster.directive.html'),
      scope: {},
      bindToController: {
        project: '=',
        cluster: '=',
        parentViewState: '=',
      },
      controller: 'ProjectClusterCtrl',
      controllerAs: 'vm',
    };
  })
  .controller('ProjectClusterCtrl', function($scope, urlBuilderService, clusterService, $q, _,
                                             collapsibleSectionStateCache, namingService, scheduler,
                                             clusterFilterService) {

    let getApplications = () => this.cluster.applications && this.cluster.applications.length ?
      this.cluster.applications : this.project.config.applications;

    let stateCache = collapsibleSectionStateCache;

    let getCacheKey = () => [this.project.name, this.cluster.account, this.cluster.stack].join(':');

    this.clearFilters = clusterFilterService.overrideFiltersForUrl;

    this.refreshTooltipTemplate = require('./projectClusterRefresh.tooltip.html');
    this.inconsistentBuildsTemplate = require('./inconsistentBuilds.tooltip.html');

    this.state = {
      loaded: false,
      expanded: stateCache.isSet(getCacheKey()) ? stateCache.isExpanded(getCacheKey()) : true,
      lastRefresh: null,
      refreshing: false,
      autoRefreshEnabled: true,
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

      let metadata = {
        type: 'clusters',
        project: this.project.name,
        application: application.name,
        cluster: clusterParam,
        stack: stackParam,
        detail: detailParam,
        account: this.cluster.account,
      };
      return metadata;
    };

    let addMetadata = () => {
      this.clusterData.applications.forEach((application) => {
        let baseMetadata = getMetadata(application);
        Object.keys(application.regions).forEach((region) => {
          baseMetadata.region = region;
          baseMetadata.href = urlBuilderService.buildFromMetadata(baseMetadata);
          application.regions[region].metadata = baseMetadata;
        });
      });
    };

    let clusterLoaders = {};

    let addInstanceCounts = (container) => {
      container.instanceCounts = {
          totalCount: 0,
          upCount: 0,
          downCount: 0,
          outOfServiceCount: 0,
          startingCount: 0,
          unknownCount: 0,
      };
    };

    let incrementInstanceCounts = (parent, container) => {
      let parentCounts = parent.instanceCounts;
      parentCounts.totalCount += container.instanceCounts.total;
      parentCounts.upCount += container.instanceCounts.up;
      parentCounts.downCount += container.instanceCounts.down;
      parentCounts.outOfServiceCount += container.instanceCounts.outOfService;
      parentCounts.startingCount += container.instanceCounts.starting;
      parentCounts.unknownCount += container.instanceCounts.unknown;
    };

    let makeBuildModel = (serverGroup) => {
      let buildModel = { number: 0 };
      if (serverGroup && serverGroup.buildInfo && serverGroup.buildInfo.jenkins) {
        let jenkins = serverGroup.buildInfo.jenkins;
        buildModel.number = Number(jenkins.number);
        buildModel.url = [jenkins.host + 'job', jenkins.name, jenkins.number, ''].join('/');
        buildModel.deployed = serverGroup.createdTime;
      }
      return buildModel;
    };

    let initializeClusterData = () => {
      this.clusterData = {
        regions: [],
        instanceCounts: {
          totalCount: 0,
          upCount: 0,
          downCount: 0,
          outOfServiceCount: 0,
          startingCount: 0,
          unknownCount: 0,
        }
      };
      this.clusterData.applications = getApplications().map((application) => {
        let metadata = getMetadata({name: application});
        metadata.href = urlBuilderService.buildFromMetadata(metadata);
        return {
          name: application,
          regions: {},
          metadata: metadata,
        };
      });
    };

    let applyRegionsAndInstanceCounts = (clusters, application) => {
      let clusterData = clusters[application];
      let serverGroups = clusterData.serverGroups || [];
      clusterData.serverGroups = serverGroups.filter((serverGroup) =>
        serverGroup && !serverGroup.disabled && serverGroup.instances.length > 0
      );
      let applicationData = _.find(this.clusterData.applications, (appData) => appData.name === application);
      applicationData.build = makeBuildModel();
      let serverGroupsByRegion = _.groupBy(clusterData.serverGroups, 'region');
      if (clusterData.serverGroups.length) {
        applicationData.lastCreatedTime = _.sortBy(clusterData.serverGroups, 'createdTime').pop().createdTime;
      }
      Object.keys(serverGroupsByRegion).forEach((region) => {
        this.clusterData.regions.push(region);
        let serverGroups = serverGroupsByRegion[region];
        let regionInfo = {
          build: makeBuildModel(),
          otherBuilds: [],
        };
        if (serverGroups && serverGroups.length) {
          regionInfo.lastCreatedTime = _.sortBy(serverGroups, 'createdTime').pop().createdTime;
        }
        addInstanceCounts(regionInfo);
        applicationData.regions[region] = regionInfo;
        applicationData.lastCreatedTime = _.sortBy(serverGroups, 'createdTime').pop().createdTime;
        serverGroups.forEach((serverGroup) => {
          serverGroup.build = makeBuildModel(serverGroup);
          if (!regionInfo.build.number) {
            regionInfo.build = serverGroup.build;
          } else {
            if (serverGroup.build.number > regionInfo.build.number) {
              regionInfo.otherBuilds.push(regionInfo.build);
              regionInfo.build = serverGroup.build;
            }
            if (serverGroup.build.number < regionInfo.build.number) {
              if (regionInfo.otherBuilds.every((build) => build.number !== serverGroup.build.number)) {
                regionInfo.otherBuilds.push(serverGroup.build);
              }
            }
          }
          incrementInstanceCounts(regionInfo, serverGroup);
          incrementInstanceCounts(this.clusterData, serverGroup);
        });
        if (applicationData.build.number <= regionInfo.build.number) {
          applicationData.build = regionInfo.build;
        }
      });
    };

    let applyInconsistentBuildFlag = (application) => {
      application.hasInconsistentBuilds = false;
      Object.keys(application.regions).forEach((regionName) => {
        let region = application.regions[regionName];
        if (region.build.number !== application.build.number || region.otherBuilds.length) {
          application.hasInconsistentBuilds = true;
          region.inconsistentBuilds = region.otherBuilds.length ? region.otherBuilds : [region.build];
        }
      });
    };


    let getClusterMatches = (clusters) => {
      let stack = this.cluster.stack,
        detail = this.cluster.detail,
        targetClusters = clusters[this.cluster.account] || [];
      return targetClusters.filter((cluster) => {
        let clusterParts = namingService.parseServerGroupName(cluster);
        if (stack !== '*' && clusterParts.stack !== stack) {
          return false;
        }
        if (detail !== '*' && clusterParts.freeFormDetails !== detail) {
          return false;
        }
        return true;
      });
    };

    let buildDataLoader = (application) => {
      if (this.cluster.stack === '*' || this.cluster.detail === '*') {
        return clusterService.getClusters(application).then((clusters) => {
          let clustersToLoad = getClusterMatches(clusters);
          return $q.all(clustersToLoad.map((cluster) => {
            return clusterService.getCluster(application, this.cluster.account, cluster);
          })).then((clusterResults) => {
            let megaCluster = { serverGroups: [] };
            clusterResults.forEach((clusterResult => {
              megaCluster.serverGroups = megaCluster.serverGroups.concat(clusterResult.serverGroups);
            }));
            return megaCluster;
          });
        });
      } else {
        let clusterName = namingService.getClusterName(application, this.cluster.stack, this.cluster.detail);
        return clusterService.getCluster(application, this.cluster.account, clusterName);
      }
    };

    let loadData = () => {
      this.state.refreshing = true;

      getApplications().map((application) => {
        clusterLoaders[application] = buildDataLoader(application);
      });
      $q.all(clusterLoaders).then((clusters) => {
        addInstanceCounts(this.clusterData);
        Object.keys(clusters).forEach((application) => {
          applyRegionsAndInstanceCounts(clusters, application);
        });
        this.clusterData.applications.forEach(applyInconsistentBuildFlag);
        this.clusterData.regions = _.uniq(this.clusterData.regions).sort();
        this.state.loaded = true;
        this.state.refreshing = false;
        this.state.lastRefresh = new Date().getTime();
        addMetadata();
      });
    };

    initializeClusterData();

    let dataLoader = scheduler.subscribe(loadData);

    $scope.$on('$destroy', () => dataLoader.dispose());

    this.refreshImmediately = scheduler.scheduleImmediate;

    loadData();

    this.clusterLabel = this.cluster.detail ? [this.cluster.stack, this.cluster.detail].join('-') : this.cluster.stack;

  });
