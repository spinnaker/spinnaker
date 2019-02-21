'use strict';

const angular = require('angular');
import _ from 'lodash';

import {
  AccountService,
  CONFIRMATION_MODAL_SERVICE,
  FirewallLabels,
  NetworkReader,
  OVERRIDE_REGISTRY,
  SECURITY_GROUP_READER,
  ServerGroupReader,
  ServerGroupWarningMessageService,
  SERVER_GROUP_WRITER,
  SubnetReader,
} from '@spinnaker/core';

require('../configure/serverGroup.configure.openstack.module');

module.exports = angular
  .module('spinnaker.serverGroup.details.openstack.controller', [
    require('@uirouter/angularjs').default,
    CONFIRMATION_MODAL_SERVICE,
    SERVER_GROUP_WRITER,
    SECURITY_GROUP_READER,
    OVERRIDE_REGISTRY,
    require('../configure/ServerGroupCommandBuilder').name,
    require('../serverGroup.transformer').name,
  ])
  .controller('openstackServerGroupDetailsCtrl', ['$scope', '$state', 'app', 'serverGroup', 'openstackServerGroupCommandBuilder', '$uibModal', 'confirmationModalService', 'serverGroupWriter', 'securityGroupReader', 'loadBalancerReader', 'openstackServerGroupTransformer', 'overrideRegistry', function(
    $scope,
    $state,
    app,
    serverGroup,
    openstackServerGroupCommandBuilder,
    $uibModal,
    confirmationModalService,
    serverGroupWriter,
    securityGroupReader,
    loadBalancerReader,
    openstackServerGroupTransformer,
    overrideRegistry,
  ) {
    var ctrl = this;
    this.state = {
      loading: true,
    };

    this.firewallsLabel = FirewallLabels.get('Firewalls');

    this.application = app;

    let extractServerGroupSummary = () => {
      return app.ready().then(() => {
        var summary = _.find(app.serverGroups.data, toCheck => {
          return (
            toCheck.name === serverGroup.name &&
            toCheck.account === serverGroup.accountId &&
            toCheck.region === serverGroup.region
          );
        });
        if (!summary) {
          app.loadBalancers.data.some(loadBalancer => {
            if (loadBalancer.account === serverGroup.accountId && loadBalancer.region === serverGroup.region) {
              return loadBalancer.serverGroups.some(possibleServerGroup => {
                if (possibleServerGroup.name === serverGroup.name) {
                  summary = possibleServerGroup;
                  return true;
                }
              });
            }
          });
        }
        return summary;
      });
    };

    let autoClose = () => {
      if ($scope.$$destroyed) {
        return;
      }
      $state.go('^', { allowModalToStayOpen: true }, { location: 'replace' });
    };

    let cancelLoader = () => {
      this.state.loading = false;
    };

    let retrieveServerGroup = () => {
      return extractServerGroupSummary()
        .then(summary => {
          return ServerGroupReader.getServerGroup(
            app.name,
            serverGroup.accountId,
            serverGroup.region,
            serverGroup.name,
          ).then(details => {
            cancelLoader();

            angular.extend(details, summary);

            // it's possible the summary was not found because the clusters are still loading
            if (!details.account) {
              details.account = serverGroup.accountId;
            }

            openstackServerGroupTransformer.normalizeServerGroup(details);

            this.serverGroup = details;
            this.applyAccountDetails(this.serverGroup);
            this.applySubnetDetails();
            this.applyFloatingIpDetails();
            this.applySecurityGroupDetails(this.serverGroup);
            this.applyLoadBalancerDetails(this.serverGroup);

            if (_.isEmpty(this.serverGroup)) {
              autoClose();
            }
          });
        })
        .catch(autoClose);
    };

    retrieveServerGroup().then(() => {
      // If the user navigates away from the view before the initial retrieveServerGroup call completes,
      // do not bother subscribing to the refresh
      if (!$scope.$$destroyed) {
        app.serverGroups.onRefresh($scope, retrieveServerGroup);
      }
    });

    this.isEnableLocked = () => {
      if (this.serverGroup.isDisabled) {
        let resizeTasks = (this.serverGroup.runningTasks || []).filter(task =>
          _.get(task, 'execution.stages', []).some(stage => stage.type === 'resizeServerGroup'),
        );
        if (resizeTasks.length) {
          return true;
        }
      }
      return false;
    };

    this.destroyServerGroup = () => {
      var serverGroup = this.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Destroying ' + serverGroup.name,
      };

      var submitMethod = params => serverGroupWriter.destroyServerGroup(serverGroup, app, params);

      var stateParams = {
        name: serverGroup.name,
        accountId: serverGroup.account,
        region: serverGroup.region,
      };

      var confirmationModalParams = {
        header: 'Really destroy ' + serverGroup.name + '?',
        buttonText: 'Destroy ' + serverGroup.name,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        submitMethod: submitMethod,
        askForReason: true,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Openstack',
        onTaskComplete: () => {
          if ($state.includes('**.serverGroup', stateParams)) {
            $state.go('^');
          }
        },
      };

      if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Openstack'];
      }

      ServerGroupWarningMessageService.addDestroyWarningMessage(app, serverGroup, confirmationModalParams);

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.disableServerGroup = () => {
      var serverGroup = this.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Disabling ' + serverGroup.name,
      };

      var submitMethod = params => {
        return serverGroupWriter.disableServerGroup(serverGroup, app, params);
      };

      var confirmationModalParams = {
        header: 'Really disable ' + serverGroup.name + '?',
        buttonText: 'Disable ' + serverGroup.name,
        account: serverGroup.account,
        provider: 'openstack',
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Openstack',
        submitMethod: submitMethod,
        askForReason: true,
      };

      if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Openstack'];
      }

      ServerGroupWarningMessageService.addDisableWarningMessage(app, serverGroup, confirmationModalParams);

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.enableServerGroup = () => {
      var serverGroup = this.serverGroup;

      var taskMonitor = {
        application: app,
        title: 'Enabling ' + serverGroup.name,
      };

      var submitMethod = params => {
        return serverGroupWriter.enableServerGroup(serverGroup, app, params);
      };

      var confirmationModalParams = {
        header: 'Really enable ' + serverGroup.name + '?',
        buttonText: 'Enable ' + serverGroup.name,
        account: serverGroup.account,
        taskMonitorConfig: taskMonitor,
        platformHealthOnlyShowOverride: app.attributes.platformHealthOnlyShowOverride,
        platformHealthType: 'Openstack',
        submitMethod: submitMethod,
        askForReason: true,
      };

      if (app.attributes.platformHealthOnlyShowOverride && app.attributes.platformHealthOnly) {
        confirmationModalParams.interestingHealthProviderNames = ['Openstack'];
      }

      confirmationModalService.confirm(confirmationModalParams);
    };

    this.rollbackServerGroup = () => {
      $uibModal.open({
        templateUrl: overrideRegistry.getTemplate(
          'openstack.rollback.modal',
          require('./rollback/rollbackServerGroup.html'),
        ),
        controller: 'openstackRollbackServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: () => this.serverGroup,
          disabledServerGroups: () => {
            var cluster = _.find(app.clusters, { name: this.serverGroup.cluster, account: this.serverGroup.account });
            return _.filter(cluster.serverGroups, { isDisabled: true, region: this.serverGroup.region });
          },
          application: () => app,
        },
      });
    };

    this.resizeServerGroup = () => {
      $uibModal.open({
        templateUrl: require('./resize/resizeServerGroup.html'),
        controller: 'openstackResizeServerGroupCtrl as ctrl',
        resolve: {
          serverGroup: () => this.serverGroup,
          application: () => app,
        },
      });
    };

    this.cloneServerGroup = serverGroup => {
      $uibModal.open({
        templateUrl: require('../configure/wizard/serverGroupWizard.html'),
        controller: 'openstackCloneServerGroupCtrl as ctrl',
        size: 'lg',
        resolve: {
          title: () => 'Clone ' + serverGroup.name,
          application: () => app,
          serverGroupCommand: () =>
            openstackServerGroupCommandBuilder.buildServerGroupCommandFromExisting(app, serverGroup),
        },
      });
    };

    this.buildJenkinsLink = () => {
      if (this.serverGroup && this.serverGroup.buildInfo && this.serverGroup.buildInfo.buildInfoUrl) {
        return this.serverGroup.buildInfo.buildInfoUrl;
      } else if (this.serverGroup && this.serverGroup.buildInfo && this.serverGroup.buildInfo.jenkins) {
        var jenkins = this.serverGroup.buildInfo.jenkins;
        return jenkins.host + 'job/' + jenkins.name + '/' + jenkins.number;
      }
      return null;
    };

    this.truncateCommitHash = () => {
      if (this.serverGroup && this.serverGroup.buildInfo && this.serverGroup.buildInfo.commit) {
        return this.serverGroup.buildInfo.commit.substring(0, 8);
      }
      return null;
    };

    this.applyAccountDetails = serverGroup => {
      return AccountService.getAccountDetails(serverGroup.account).then(details => {
        serverGroup.accountDetails = details;
      });
    };

    this.applySecurityGroupDetails = serverGroup => {
      return securityGroupReader.loadSecurityGroups().then(allSecurityGroups => {
        var accountIndex = allSecurityGroups[serverGroup.account] || {};
        var regionSecurityGroups = accountIndex[serverGroup.region] || {};
        $scope.securityGroups = _.map(serverGroup.launchConfig.securityGroups, sgId => {
          //TODO(jcwest): remove this once the back-end sends correctly formatted firewall IDs
          if (new RegExp("^\\[u'").test(sgId)) {
            sgId = sgId.split("'")[1];
          }
          return regionSecurityGroups[sgId] || { id: sgId, name: sgId };
        });
      });
    };

    this.applyLoadBalancerDetails = serverGroup => {
      return loadBalancerReader.loadLoadBalancers(app.name).then(allLoadBalancers => {
        var lbIndex = {};
        _.forEach(allLoadBalancers, lb => {
          lbIndex[lb.name] = lb;
        });
        $scope.loadBalancers = _.chain(serverGroup.loadBalancers)
          .map(lbName => {
            return lbIndex[lbName];
          })
          .compact()
          .value();
      });
    };

    this.applySubnetDetails = () => {
      return SubnetReader.getSubnetByIdAndProvider(this.serverGroup.subnetId, 'openstack').then(details => {
        ctrl.subnetName = (details || {}).name;
      });
    };

    this.applyFloatingIpDetails = () => {
      return NetworkReader.listNetworksByProvider('openstack').then(networks => {
        ctrl.floatingNetworkName = (
          _.find(networks, net => {
            return net.id === ctrl.serverGroup.launchConfig.floatingNetworkId;
          }) || {}
        ).name;
      });
    };
  }]);
