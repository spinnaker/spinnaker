import {module} from 'angular';
import {filter, flatten, map, each} from 'lodash';
import {IModalService} from 'angular-ui-bootstrap';

import {AuthenticationService} from 'core/authentication/authentication.service';
import {CLOUD_PROVIDER_REGISTRY, CloudProviderRegistry} from 'core/cloudProvider/cloudProvider.registry';
import {ICluster, IStage, ServerGroup} from 'core/domain/index';
import {LIST_EXTRACTOR_SERVICE} from 'core/application/listExtractor/listExtractor.service';
import {SERVER_GROUP_COMMAND_BUILDER_SERVICE} from 'core/serverGroup/configure/common/serverGroupCommandBuilder.service';
import {SERVER_GROUP_READER, ServerGroupReader} from 'core/serverGroup/serverGroupReader.service';
import {NAMING_SERVICE, NamingService} from 'core/naming/naming.service';
import {EDIT_VIP_MODAL_CONTROLLER} from './editVip.modal.controller';

interface IVipOverride {
  oldVip: string;
  oldSecureVip: string;
  newVip: string;
  newSecureVip: string;
  [key: string]: string; // This makes the linter happy when an object of shape IVipOverride is referenced with a variable instead of dot notation
}

interface IIsolatedTestingTargetStage extends IStage {
  clusters: ICluster[];
  vipOverrides: {
    [clusterId: string]: IVipOverride;
  };
  username: string;
}

class IsolatedTestingTargetStageCtrl {
  static get $inject() {
    return ['$scope', '$uibModal', 'stage', 'namingService', 'providerSelectionService',
            'authenticationService', 'cloudProviderRegistry', 'serverGroupCommandBuilder',
            'serverGroupReader', 'awsServerGroupTransformer'];
  }

  constructor(private $scope: any,
              private $uibModal: IModalService,
              private stage: IIsolatedTestingTargetStage,
              private namingService: NamingService,
              private providerSelectionService: any,
              authenticationService: AuthenticationService,
              private cloudProviderRegistry: CloudProviderRegistry,
              private serverGroupCommandBuilder: any,
              private serverGroupReader: ServerGroupReader,
              private awsServerGroupTransformer: any) {

    const user = authenticationService.getAuthenticatedUser();
    $scope.stage = stage;
    $scope.stage.owner = $scope.stage.owner || (user.authenticated ? user.name : null);

    $scope.stage.username = user.name.includes('@') ? user.name.substring(0, user.name.lastIndexOf('@')) : user.name;
  }

  public getClusterOldVIPs(cluster: any): string {
    const vips = this.stage.vipOverrides[this.getClusterId(cluster)];
    return vips ? [vips.oldVip, vips.oldSecureVip].filter(n => n).join(', ') : '';
  };

  public getClusterNewVIPs(cluster: any): string {
    const vips = this.stage.vipOverrides[this.getClusterId(cluster)];
    return vips ? [vips.newVip, vips.newSecureVip].filter(n => n).join(', ') : '';
  };

  public getClusterName(cluster: any): string {
    return this.namingService.getClusterName(cluster.application, cluster.stack, cluster.freeFormDetails);
  }

  private buildServerGroupCommand(selectedProvider: string): ng.IPromise<any> {
    return this.serverGroupCommandBuilder.buildNewServerGroupCommandForPipeline(selectedProvider)
      .then((command: any) => {
        this.configureServerGroupCommandForEditing(command);
        command.viewState.overrides = {
          useSourceCapacity: false,
          capacity: {
            min: 1, max: 1, desired: 1,
          },
          loadBalancers: [],
          freeFormDetails: 'itt-' + this.stage.username,
          maxRemainingAsgs: 2
        };
        command.viewState.disableNoTemplateSelection = true;
        command.viewState.customTemplateMessage = 'Select a template to configure the isolated testing target cluster. ' +
          'If you want to configure the server groups differently, you can do so by clicking ' +
          '"Edit" after adding the cluster.';
        return command;
      });
  }

  private applyCommandToStage(command: any): void {
    // Push the cluster config into the list of clusters
    const cluster = this.awsServerGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
    this.stage.clusters.push(cluster);

    // If the ASG we are cloning has a VIP, generate the new VIP
    if (command.source && command.source.asgName && command.source.region && command.source.account) {
      this.serverGroupReader.getServerGroup(command.application, command.source.account, command.source.region, command.source.asgName).then((asgDetails: ServerGroup) => {
        if (asgDetails) {
          const discoveryHealth = filter(flatten(map(asgDetails.instances, 'health')), { type: 'Discovery' });

          let oldVip: string, oldSecureVip: string;
          each(discoveryHealth, (health: any) => {
            oldVip = oldVip || health.vipAddress;
            oldSecureVip = oldSecureVip || health.secureVipAddress;
          });

          const newVip = this.generateNewVip(oldVip, command.freeFormDetails);
          const newSecureVip = this.generateNewVip(oldSecureVip, command.freeFormDetails);
          this.stage.vipOverrides[this.getClusterId(cluster)] = { oldVip, oldSecureVip, newVip, newSecureVip };
        }
      });
    }
  }

  public addCluster(): void {
    this.stage.clusters = this.stage.clusters || [];
    this.stage.vipOverrides = this.stage.vipOverrides || {};
    this.providerSelectionService.selectProvider(this.$scope.application).then((selectedProvider: string) => {
      let config = this.cloudProviderRegistry.getValue(selectedProvider, 'serverGroup');
      this.$uibModal.open({
        templateUrl: config.cloneServerGroupTemplateUrl,
        controller: `${config.cloneServerGroupController} as ctrl`,
        size: 'lg',
        resolve: {
          title: () => 'Add Isolated Testing Target Cluster',
          application: () => this.$scope.application,
          serverGroupCommand: () => this.buildServerGroupCommand(selectedProvider),
        }
      }).result.then((command: any) => this.applyCommandToStage(command));
    });
  }

  public editCluster(cluster: any, index: number): void {
    cluster.provider = cluster.provider || 'aws';
    let config = this.cloudProviderRegistry.getValue(cluster.provider, 'serverGroup');
    this.$uibModal.open({
      templateUrl: config.cloneServerGroupTemplateUrl,
      controller: `${config.cloneServerGroupController} as ctrl`,
      size: 'lg',
      resolve: {
        title: () => 'Configure Isolated Testing Target Cluster',
        application: () => this.$scope.application,
        serverGroupCommand: () => {
          return this.serverGroupCommandBuilder.buildServerGroupCommandFromPipeline(this.$scope.application, cluster)
            .then((command: any) => this.configureServerGroupCommandForEditing(command));
        }
      }
    }).result.then((command: any) => {
        this.stage.clusters[index] = this.awsServerGroupTransformer.convertServerGroupCommandToDeployConfiguration(command);
      });
  }

  public deleteCluster(index: number): void {
    this.stage.clusters.splice(index, 1);
  }

  public editVip(cluster: any, vipType: 'oldVip' | 'newVip'): void {
    const clusterVip = this.stage.vipOverrides[this.getClusterId(cluster)];
    this.$uibModal.open({
      templateUrl: require('./editVip.modal.html'),
      controller: 'EditVipModalCtrl as vm',
      size: 'md',
      resolve: {
        vip: () => clusterVip[vipType]
      }
    }).result.then(newVip => {
      clusterVip[vipType] = newVip;
    });
  }

  private getClusterId(cluster: any): string {
    return `${this.getRegion(cluster)}::${cluster.account}::${this.getClusterName(cluster)}`;
  }

  private configureServerGroupCommandForEditing(command: any) {
    command.viewState.disableStrategySelection = false;
    command.viewState.hideClusterNamePreview = false;
    command.viewState.readOnlyFields = { credentials: true, region: true, subnet: true };

    return command;
  }

  private generateNewVip(oldVip: string, details: string) {
    if (!oldVip) { return undefined; }

    let vipParts = oldVip.split(':');

    // Check if the last part is a port
    const lastPart = vipParts.pop();
    if (!Number(lastPart)) {
      vipParts.push(lastPart);
    }
    vipParts = [vipParts.join(':')];

    // add the isolatedTestingTarget custom vip piece
    vipParts.push(`-${details}`);

    if (Number(lastPart)) {
      vipParts.push(`:${lastPart}`);
    }

    return vipParts.join('');
  }

  // TODO: Extract into utility class
  private getRegion(cluster: any): string {
    if (cluster.region) {
      return cluster.region;
    }
    const availabilityZones = cluster.availabilityZones;
    if (availabilityZones) {
      const regions = Object.keys(availabilityZones);
      if (regions && regions.length) {
        return regions[0];
      }
    }
    return 'n/a';
  }
}

export const ISOLATED_TESTING_TARGET_STAGE = 'spinnaker.netflix.pipeline.stage.isolatedTestingTargetStage';

module(ISOLATED_TESTING_TARGET_STAGE, [
  require('core/pipeline/config/pipelineConfigProvider'),
  LIST_EXTRACTOR_SERVICE,
  EDIT_VIP_MODAL_CONTROLLER,
  NAMING_SERVICE,
  CLOUD_PROVIDER_REGISTRY,
  SERVER_GROUP_COMMAND_BUILDER_SERVICE,
  SERVER_GROUP_READER,
  require('core/config/settings.js'),
])
  .config((pipelineConfigProvider: any, settings: any) => {
    if (settings.feature && settings.feature.netflixMode) {
      pipelineConfigProvider.registerStage({
        label: 'Isolated Testing Target',
        description: 'Launches a cluster with a unique VIP to allow for testing on an isolated cluster',
        key: 'isolatedTestingTarget',
        cloudProviders: ['aws'],
        templateUrl: require('./isolatedTestingTargetStage.html'),
        controller: 'IsolatedTestingTargetStageCtrl',
        controllerAs: 'isolatedTestingTargetStageCtrl',
        validators: [
          {
            type: 'stageBeforeType',
            stageTypes: ['bake', 'findAmi', 'findImage'],
            message: 'You must have a Bake or Find AMI stage before an Isolated Testing Target stage.'
          },
        ],
      });
    }
  })
  .controller('IsolatedTestingTargetStageCtrl', IsolatedTestingTargetStageCtrl);
