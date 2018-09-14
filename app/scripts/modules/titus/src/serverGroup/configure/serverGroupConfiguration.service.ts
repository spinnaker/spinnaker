import { module } from 'angular';
import { chain, flatten, intersection, xor } from 'lodash';
import { $q } from 'ngimport';
import { Subject } from 'rxjs';

import {
  AccountService,
  IServerGroupCommand,
  IServerGroupCommandViewState,
  IDeploymentStrategy,
  IServerGroupCommandBackingData,
  CacheInitializerService,
  CACHE_INITIALIZER_SERVICE,
  LoadBalancerReader,
  LOAD_BALANCER_READ_SERVICE,
  ICluster,
  IAccountDetails,
} from '@spinnaker/core';
import { IAmazonApplicationLoadBalancer, IAmazonLoadBalancer, IAmazonServerGroupCommandDirty } from '@spinnaker/amazon';

export interface ITitusServerGroupCommandBackingData extends IServerGroupCommandBackingData {
  accounts: string[];
}

export interface ITitusServerGroupCommandViewState extends IServerGroupCommandViewState {
  accountChangedStream: Subject<{}>;
  regionChangedStream: Subject<{}>;
  groupsRemovedStream: Subject<{}>;
  dirty: IAmazonServerGroupCommandDirty;
}

export interface ITitusServerGroupCommand extends IServerGroupCommand {
  cluster?: ICluster;
  registry: string;
  imageId: string;
  organization: string;
  repository: string;
  tag: string;
  image: string;
  inService: boolean;
  resources: {
    cpu: number;
    memory: number;
    disk: number;
    networkMbps: number;
    gpu: number;
  };
  efs: {
    efsId: string;
    mountPoint: string;
    mountPerm: string;
    efsRelativeMountPoint: string;
  };
  viewState: ITitusServerGroupCommandViewState;
  targetGroups: string[];
  removedTargetGroups: string[];
}

export class TitusServerGroupConfigurationService {
  constructor(private cacheInitializer: CacheInitializerService, private loadBalancerReader: LoadBalancerReader) {
    'ngInject';
  }

  public configureZones(command: ITitusServerGroupCommand) {
    command.backingData.filtered.regions = command.backingData.credentialsKeyedByAccount[command.credentials].regions;
  }

  private attachEventHandlers(cmd: ITitusServerGroupCommand) {
    cmd.viewState.dirty = {};
    cmd.credentialsChanged = () => {
      const result = { dirty: {} };
      const backingData = cmd.backingData;
      this.configureZones(cmd);
      if (cmd.credentials) {
        cmd.registry = (backingData.credentialsKeyedByAccount[cmd.credentials] as any).registry;
        backingData.filtered.regions = backingData.credentialsKeyedByAccount[cmd.credentials].regions;
        if (!backingData.filtered.regions.some(r => r.name === cmd.region)) {
          cmd.region = null;
          cmd.regionChanged(cmd);
        }
      } else {
        cmd.region = null;
      }
      cmd.viewState.dirty = { ...(cmd.viewState.dirty || {}), ...result.dirty };
      this.configureLoadBalancerOptions(cmd);
      return result;
    };

    cmd.regionChanged = (command: ITitusServerGroupCommand) => {
      this.configureLoadBalancerOptions(command);
      return {};
    };
  }

  public configureCommand(cmd: ITitusServerGroupCommand) {
    cmd.viewState.accountChangedStream = new Subject();
    cmd.viewState.regionChangedStream = new Subject();
    cmd.viewState.groupsRemovedStream = new Subject();
    cmd.onStrategyChange = (command: ITitusServerGroupCommand, strategy: IDeploymentStrategy) => {
      // Any strategy other than None or Custom should force traffic to be enabled
      if (strategy.key !== '' && strategy.key !== 'custom') {
        command.inService = true;
      }
    };
    cmd.image = cmd.viewState.imageId;
    return $q
      .all({
        credentialsKeyedByAccount: AccountService.getCredentialsKeyedByAccount('titus'),
        images: [],
      })
      .then((backingData: any) => {
        backingData.accounts = Object.keys(backingData.credentialsKeyedByAccount);
        backingData.filtered = {};
        backingData.filtered.regions = backingData.credentialsKeyedByAccount[cmd.credentials].regions;
        cmd.backingData = backingData;

        // this.configureLoadBalancerOptions(cmd);

        return $q.all([this.refreshLoadBalancers(cmd)]).then(() => {
          this.attachEventHandlers(cmd);
        });
      });
  }

  private getCredentials(command: ITitusServerGroupCommand): IAccountDetails {
    return command.backingData.credentialsKeyedByAccount[command.credentials];
  }

  private getAwsAccount(command: ITitusServerGroupCommand): string {
    return this.getCredentials(command).awsAccount;
  }

  private getRegion(command: ITitusServerGroupCommand): string {
    return command.region || (command.cluster ? command.cluster.region : null);
  }

  public getTargetGroupNames(command: ITitusServerGroupCommand): string[] {
    const loadBalancersV2 = this.getLoadBalancerMap(command).filter(
      lb => lb.loadBalancerType !== 'classic',
    ) as IAmazonApplicationLoadBalancer[];
    const instanceTargetGroups = flatten(
      loadBalancersV2.map<any>(lb => lb.targetGroups.filter(tg => tg.targetType === 'ip')),
    );
    return instanceTargetGroups.map(tg => tg.name).sort();
  }

  private getLoadBalancerMap(command: ITitusServerGroupCommand): IAmazonLoadBalancer[] {
    return chain(command.backingData.loadBalancers)
      .map('accounts')
      .flattenDeep()
      .filter({ name: this.getAwsAccount(command) })
      .map('regions')
      .flattenDeep()
      .filter({ name: this.getRegion(command) })
      .map<IAmazonLoadBalancer>('loadBalancers')
      .flattenDeep<IAmazonLoadBalancer>()
      .value();
  }

  public configureLoadBalancerOptions(command: ITitusServerGroupCommand) {
    const currentTargetGroups = command.targetGroups || [];
    const allTargetGroups = this.getTargetGroupNames(command);

    if (currentTargetGroups && command.targetGroups) {
      const matched = intersection(allTargetGroups, currentTargetGroups);
      const removedTargetGroups = xor(matched, currentTargetGroups);
      command.targetGroups = intersection(allTargetGroups, matched);
      command.viewState.dirty.targetGroups = removedTargetGroups;
    }
    (command.backingData.filtered as any).targetGroups = allTargetGroups;
  }

  public refreshLoadBalancers(command: ITitusServerGroupCommand) {
    return this.cacheInitializer.refreshCache('loadBalancers').then(() => {
      return this.loadBalancerReader.listLoadBalancers('aws').then(loadBalancers => {
        command.backingData.loadBalancers = loadBalancers;
        this.configureLoadBalancerOptions(command);
      });
    });
  }
}

export const TITUS_SERVER_GROUP_CONFIGURATION_SERVICE = 'spinnaker.titus.serverGroup.configure.service';
module(TITUS_SERVER_GROUP_CONFIGURATION_SERVICE, [CACHE_INITIALIZER_SERVICE, LOAD_BALANCER_READ_SERVICE]).service(
  'titusServerGroupConfigurationService',
  TitusServerGroupConfigurationService,
);
