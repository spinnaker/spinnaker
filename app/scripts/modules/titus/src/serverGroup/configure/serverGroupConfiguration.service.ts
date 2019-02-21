import { IPromise, module } from 'angular';
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
  SECURITY_GROUP_READER,
  SecurityGroupReader,
  IVpc,
  ISecurityGroup,
} from '@spinnaker/core';
import {
  IAmazonApplicationLoadBalancer,
  IAmazonLoadBalancer,
  IAmazonServerGroupCommandDirty,
  VpcReader,
} from '@spinnaker/amazon';

export interface ITitusServerGroupCommandBackingData extends IServerGroupCommandBackingData {
  accounts: string[];
  vpcs: IVpc[];
}

export interface ITitusServerGroupCommandViewState extends IServerGroupCommandViewState {
  accountChangedStream: Subject<{}>;
  regionChangedStream: Subject<{}>;
  groupsRemovedStream: Subject<{}>;
  dirty: IAmazonServerGroupCommandDirty;
}

export type Constraint = 'ExclusiveHost' | 'UniqueHost' | 'ZoneBalance';
export interface ITitusServerGroupCommand extends IServerGroupCommand {
  cluster?: ICluster;
  deferredInitialization?: boolean;
  registry: string;
  imageId: string;
  organization: string;
  repository: string;
  tag?: string;
  digest?: string;
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
  backingData: ITitusServerGroupCommandBackingData;
  labels: { [key: string]: string };
  containerAttributes: { [key: string]: string };
  env: { [key: string]: string };
  migrationPolicy: {
    type: string;
  };
  softConstraints: Constraint[];
  hardConstraints: Constraint[];
}

export class TitusServerGroupConfigurationService {
  public static $inject = ['cacheInitializer', 'loadBalancerReader', 'securityGroupReader'];
  constructor(
    private cacheInitializer: CacheInitializerService,
    private loadBalancerReader: LoadBalancerReader,
    private securityGroupReader: SecurityGroupReader,
  ) {
    'ngInject';
  }

  public configureZones(command: ITitusServerGroupCommand) {
    command.backingData.filtered.regions = command.backingData.credentialsKeyedByAccount[command.credentials].regions;
  }

  private attachEventHandlers(cmd: ITitusServerGroupCommand) {
    cmd.credentialsChanged = (command: ITitusServerGroupCommand) => {
      const result = { dirty: {} };
      const backingData = command.backingData;
      this.configureZones(command);
      if (command.credentials) {
        command.registry = (backingData.credentialsKeyedByAccount[command.credentials] as any).registry;
        backingData.filtered.regions = backingData.credentialsKeyedByAccount[command.credentials].regions;
        if (!backingData.filtered.regions.some(r => r.name === command.region)) {
          command.region = null;
          command.regionChanged(command);
        }
      } else {
        command.region = null;
      }
      command.viewState.dirty = { ...(command.viewState.dirty || {}), ...result.dirty };
      this.configureLoadBalancerOptions(command);
      this.configureSecurityGroupOptions(command);
      return result;
    };

    cmd.regionChanged = (command: ITitusServerGroupCommand) => {
      this.configureLoadBalancerOptions(command);
      this.configureSecurityGroupOptions(command);
      return {};
    };
  }

  public configureCommand(cmd: ITitusServerGroupCommand) {
    cmd.viewState.accountChangedStream = new Subject();
    cmd.viewState.regionChangedStream = new Subject();
    cmd.viewState.groupsRemovedStream = new Subject();
    cmd.viewState.dirty = {};
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
        securityGroups: this.securityGroupReader.getAllSecurityGroups(),
        vpcs: VpcReader.listVpcs(),
        images: [],
      })
      .then((backingData: any) => {
        backingData.accounts = Object.keys(backingData.credentialsKeyedByAccount);
        backingData.filtered = {};
        backingData.filtered.regions = backingData.credentialsKeyedByAccount[cmd.credentials].regions;
        cmd.backingData = backingData;

        return $q.all([this.refreshLoadBalancers(cmd), this.refreshSecurityGroups(cmd, false)]).then(() => {
          this.attachEventHandlers(cmd);
        });
      });
  }

  private getVpcId(command: ITitusServerGroupCommand): string {
    const credentials = this.getCredentials(command);
    const match = command.backingData.vpcs.find(
      vpc =>
        vpc.name === credentials.awsVpc &&
        vpc.account === credentials.awsAccount &&
        vpc.region === this.getRegion(command) &&
        vpc.cloudProvider === 'aws',
    );
    return match ? match.id : null;
  }

  private getRegionalSecurityGroups(command: ITitusServerGroupCommand): ISecurityGroup[] {
    const newSecurityGroups: any = command.backingData.securityGroups[this.getAwsAccount(command)] || { aws: {} };
    return chain<ISecurityGroup>(newSecurityGroups.aws[this.getRegion(command)])
      .filter({ vpcId: this.getVpcId(command) })
      .sortBy('name')
      .value();
  }

  private configureSecurityGroupOptions(command: ITitusServerGroupCommand): void {
    const currentOptions = command.backingData.filtered.securityGroups;
    const newRegionalSecurityGroups = this.getRegionalSecurityGroups(command);
    const isExpression =
      typeof command.securityGroups === 'string' && (command.securityGroups as string).includes('${');
    if (currentOptions && command.securityGroups && !isExpression) {
      // not initializing - we are actually changing groups
      const currentGroupNames: string[] = command.securityGroups.map((groupId: string) => {
        const match = currentOptions.find(o => o.id === groupId);
        return match ? match.name : groupId;
      });

      const matchedGroups = command.securityGroups
        .map((groupId: string) => {
          const securityGroup: any = currentOptions.find(o => o.id === groupId || o.name === groupId);
          return securityGroup ? securityGroup.name : null;
        })
        .map((groupName: string) => newRegionalSecurityGroups.find(g => g.name === groupName))
        .filter((group: any) => group);

      const matchedGroupNames: string[] = matchedGroups.map(g => g.name);
      const removed: string[] = xor(currentGroupNames, matchedGroupNames);
      command.securityGroups = matchedGroups.map(g => g.id);
      if (removed.length) {
        command.dirty.securityGroups = removed;
      }
    }
    command.backingData.filtered.securityGroups = newRegionalSecurityGroups.sort((a, b) => {
      if (command.securityGroups.includes(a.id)) {
        return -1;
      }
      if (command.securityGroups.includes(b.id)) {
        return 1;
      }
      return a.name.localeCompare(b.name);
    });
  }

  public refreshSecurityGroups(command: ITitusServerGroupCommand, skipCommandReconfiguration: boolean): IPromise<void> {
    return this.cacheInitializer.refreshCache('securityGroups').then(() => {
      return this.securityGroupReader.getAllSecurityGroups().then((securityGroups: any) => {
        command.backingData.securityGroups = securityGroups;
        if (!skipCommandReconfiguration) {
          this.configureSecurityGroupOptions(command);
        }
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
      if (removedTargetGroups && removedTargetGroups.length > 0) {
        command.viewState.dirty.targetGroups = removedTargetGroups;
      } else {
        delete command.viewState.dirty.targetGroups;
      }
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
module(TITUS_SERVER_GROUP_CONFIGURATION_SERVICE, [
  CACHE_INITIALIZER_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  SECURITY_GROUP_READER,
]).service('titusServerGroupConfigurationService', TitusServerGroupConfigurationService);
