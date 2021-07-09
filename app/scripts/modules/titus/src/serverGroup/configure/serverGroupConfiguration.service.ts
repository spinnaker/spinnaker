import { module } from 'angular';
import { chain, cloneDeep, flatten, intersection, xor } from 'lodash';
import { $q } from 'ngimport';
import { Subject } from 'rxjs';

import {
  IAmazonApplicationLoadBalancer,
  IAmazonLoadBalancer,
  IAmazonServerGroupCommandDirty,
  VpcReader,
} from '@spinnaker/amazon';
import {
  AccountService,
  Application,
  CACHE_INITIALIZER_SERVICE,
  CacheInitializerService,
  IAccountDetails,
  ICluster,
  IDeploymentStrategy,
  ISecurityGroup,
  IServerGroupCommand,
  IServerGroupCommandBackingData,
  IServerGroupCommandViewState,
  IVpc,
  LOAD_BALANCER_READ_SERVICE,
  LoadBalancerReader,
  NameUtils,
  SECURITY_GROUP_READER,
  SecurityGroupReader,
  setMatchingResourceSummary,
  SubnetReader,
} from '@spinnaker/core';
import { IJobDisruptionBudget, ITitusResources } from '../../domain';
import { ITitusServiceJobProcesses } from '../../domain/ITitusServiceJobProcesses';

export interface ITitusServerGroupCommandBackingData extends IServerGroupCommandBackingData {
  accounts: string[];
  vpcs: IVpc[];
}

export interface ITitusServerGroupCommandViewState extends IServerGroupCommandViewState {
  accountChangedStream: Subject<{}>;
  regionChangedStream: Subject<{}>;
  groupsRemovedStream: Subject<{}>;
  dirty: IAmazonServerGroupCommandDirty;
  defaultIamProfile: string;
}

export const defaultJobDisruptionBudget: IJobDisruptionBudget = {
  availabilityPercentageLimit: {
    percentageOfHealthyContainers: 95,
  },
  ratePercentagePerInterval: {
    intervalMs: 600000,
    percentageLimitPerInterval: 5,
  },
  containerHealthProviders: [{ name: 'eureka' }],
  timeWindows: [
    {
      days: ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'],
      hourlyTimeWindows: [{ startHour: 10, endHour: 16 }],
      timeZone: 'PST',
    },
  ],
};

export const getDefaultJobDisruptionBudgetForApp = (application: Application): IJobDisruptionBudget => {
  const budget = cloneDeep(defaultJobDisruptionBudget);
  if (application.attributes && application.attributes.platformHealthOnly) {
    budget.containerHealthProviders = [];
  }
  return budget;
};

export interface ITitusServerGroupCommand extends IServerGroupCommand {
  cluster?: ICluster;
  disruptionBudget?: IJobDisruptionBudget;
  deferredInitialization?: boolean;
  registry: string;
  imageId: string;
  organization: string;
  repository: string;
  tag?: string;
  digest?: string;
  image: string;
  inService: boolean;
  resources: ITitusResources;
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
  iamProfile: string;
  migrationPolicy: {
    type: string;
  };
  constraints: {
    hard: { [key: string]: string };
    soft: { [key: string]: string };
  };
  serviceJobProcesses: ITitusServiceJobProcesses;
}

export class TitusServerGroupConfigurationService {
  public static $inject = ['cacheInitializer', 'loadBalancerReader', 'securityGroupReader'];
  constructor(
    private cacheInitializer: CacheInitializerService,
    private loadBalancerReader: LoadBalancerReader,
    private securityGroupReader: SecurityGroupReader,
  ) {}

  public configureZones(command: ITitusServerGroupCommand) {
    command.backingData.filtered.regions = command.backingData.credentialsKeyedByAccount[command.credentials].regions;
  }

  public configureSubnets(command: ITitusServerGroupCommand) {
    const result = { dirty: {} };
    const filteredData = command.backingData.filtered;
    if (command.region === null) {
      return result;
    }

    const accountDetails = this.getCredentials(command);
    filteredData.subnetPurposes = chain(command.backingData.subnets)
      .filter({ account: accountDetails.awsAccount, region: command.region })
      .reject({ target: 'elb' })
      .reject({ purpose: null })
      .uniqBy('purpose')
      .value();

    const subnetType = command.subnetType || 'titus (vpc0)';
    const subnetIds = chain(command.backingData.subnets)
      .filter({ account: accountDetails.awsAccount, region: command.region, purpose: subnetType })
      .map((subnet) => subnet.id)
      .value();
    command.containerAttributes['titusParameter.agent.subnets'] = subnetIds.join(',');

    return result;
  }

  private attachEventHandlers(cmd: ITitusServerGroupCommand) {
    cmd.credentialsChanged = (command: ITitusServerGroupCommand) => {
      const result = { dirty: {} };
      const backingData = command.backingData;
      this.configureZones(command);
      if (command.credentials) {
        command.registry = (backingData.credentialsKeyedByAccount[command.credentials] as any).registry;
        backingData.filtered.regions = backingData.credentialsKeyedByAccount[command.credentials].regions;

        const accountId = backingData.credentialsKeyedByAccount[command.credentials].accountId;
        command.containerAttributes['titusParameter.agent.accountId'] = accountId;
        if (!backingData.filtered.regions.some((r) => r.name === command.region)) {
          command.region = null;
          command.regionChanged(command);
        }
      } else {
        command.region = null;
      }
      command.viewState.dirty = { ...(command.viewState.dirty || {}), ...result.dirty };
      this.configureLoadBalancerOptions(command);
      this.configureSecurityGroupOptions(command);
      this.configureSubnets(command);
      setMatchingResourceSummary(command);
      return result;
    };

    cmd.regionChanged = (command: ITitusServerGroupCommand) => {
      this.configureLoadBalancerOptions(command);
      this.configureSecurityGroupOptions(command);
      this.configureSubnets(command);
      setMatchingResourceSummary(command);
      return {};
    };

    cmd.clusterChanged = (command: ITitusServerGroupCommand): void => {
      command.moniker = NameUtils.getMoniker(command.application, command.stack, command.freeFormDetails);
      setMatchingResourceSummary(command);
    };

    cmd.subnetChanged = (command: ITitusServerGroupCommand) => {
      return this.configureSubnets(command);
    };
  }

  public configureCommand(cmd: ITitusServerGroupCommand): PromiseLike<void> {
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
      .all([
        AccountService.getCredentialsKeyedByAccount('titus'),
        this.securityGroupReader.getAllSecurityGroups(),
        VpcReader.listVpcs(),
        SubnetReader.listSubnets(),
        AccountService.getCredentialsKeyedByAccount('aws'),
      ])
      .then(([credentialsKeyedByAccount, securityGroups, vpcs, subnets, awsCredentials]) => {
        const backingData: any = {
          credentialsKeyedByAccount,
          securityGroups,
          vpcs,
        };

        // add the AWS accountId to the credentialsKeyedByAccount to easily set container attributes
        Object.keys(credentialsKeyedByAccount).forEach((acc) => {
          const awsAccount = credentialsKeyedByAccount[acc].awsAccount;
          const awsAccountId = awsCredentials[awsAccount].accountId;
          credentialsKeyedByAccount[acc].accountId = awsAccountId;
        });
        backingData.credentialsKeyedByAccount = credentialsKeyedByAccount;

        if (cmd.credentials) {
          cmd.containerAttributes['titusParameter.agent.accountId'] =
            backingData.credentialsKeyedByAccount[cmd.credentials].accountId;
        }

        backingData.images = [];
        backingData.accounts = Object.keys(credentialsKeyedByAccount);
        backingData.subnets = subnets;
        backingData.filtered = {};
        if (cmd.credentials.includes('${')) {
          // If our dependency is an expression, the only thing we can really do is to just preserve current selections
          backingData.filtered.regions = [{ name: cmd.region }];
        } else {
          backingData.filtered.regions = credentialsKeyedByAccount[cmd.credentials]?.regions ?? [];
        }
        cmd.backingData = backingData;
        backingData.filtered.securityGroups = this.getRegionalSecurityGroups(cmd);

        let securityGroupRefresher: PromiseLike<any> = $q.when();
        if (cmd.securityGroups && cmd.securityGroups.length) {
          const regionalSecurityGroupIds = backingData.filtered.securityGroups.map((g: ISecurityGroup) => g.id);
          if (intersection(cmd.securityGroups, regionalSecurityGroupIds).length < cmd.securityGroups.length) {
            securityGroupRefresher = this.refreshSecurityGroups(cmd, false);
          }
        }

        return $q.all([this.refreshLoadBalancers(cmd), securityGroupRefresher]).then(() => {
          this.attachEventHandlers(cmd);
        });
      });
  }

  private getVpcId(command: ITitusServerGroupCommand): string {
    const credentials = this.getCredentials(command);
    const match = command.backingData.vpcs.find(
      (vpc) =>
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
    if (command.credentials.includes('${') || (command.region && command.region.includes('${'))) {
      // If any of our dependencies are expressions, the only thing we can do is preserve current values
      command.backingData.filtered.securityGroups = command.securityGroups.map((group) => ({ name: group, id: group }));
    } else {
      const newRegionalSecurityGroups = this.getRegionalSecurityGroups(command);
      const isExpression =
        typeof command.securityGroups === 'string' && (command.securityGroups as string).includes('${');
      if (currentOptions && command.securityGroups && !isExpression) {
        // not initializing - we are actually changing groups
        const currentGroupNames: string[] = command.securityGroups.map((groupId: string) => {
          const match = currentOptions.find((o) => o.id === groupId);
          return match ? match.name : groupId;
        });

        const matchedGroups = command.securityGroups
          .map((groupId: string) => {
            const securityGroup: any = currentOptions.find((o) => o.id === groupId || o.name === groupId);
            return securityGroup ? securityGroup.name : null;
          })
          .map((groupName: string) => newRegionalSecurityGroups.find((g) => g.name === groupName))
          .filter((group: any) => group);

        const matchedGroupNames: string[] = matchedGroups.map((g) => g.name);
        const removed: string[] = xor(currentGroupNames, matchedGroupNames);
        command.securityGroups = matchedGroups.map((g) => g.id);
        if (removed.length) {
          command.viewState.dirty.securityGroups = removed;
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
  }

  public refreshSecurityGroups(
    command: ITitusServerGroupCommand,
    skipCommandReconfiguration: boolean,
  ): PromiseLike<void> {
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
      (lb) => lb.loadBalancerType !== 'classic',
    ) as IAmazonApplicationLoadBalancer[];
    const instanceTargetGroups = flatten(
      loadBalancersV2.map<any>((lb) => lb.targetGroups.filter((tg) => tg.targetType === 'ip')),
    );
    return instanceTargetGroups.map((tg) => tg.name).sort();
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
    if (command.credentials.includes('${') || (command.region && command.region.includes('${'))) {
      // If any of our dependencies are expressions, the only thing we can do is preserve current values
      command.targetGroups = currentTargetGroups;
      (command.backingData.filtered as any).targetGroups = currentTargetGroups;
    } else {
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
  }

  public refreshLoadBalancers(command: ITitusServerGroupCommand) {
    return this.loadBalancerReader.listLoadBalancers('aws').then((loadBalancers) => {
      command.backingData.loadBalancers = loadBalancers;
      this.configureLoadBalancerOptions(command);
    });
  }
}

export const TITUS_SERVER_GROUP_CONFIGURATION_SERVICE = 'spinnaker.titus.serverGroup.configure.service';
module(TITUS_SERVER_GROUP_CONFIGURATION_SERVICE, [
  CACHE_INITIALIZER_SERVICE,
  LOAD_BALANCER_READ_SERVICE,
  SECURITY_GROUP_READER,
]).service('titusServerGroupConfigurationService', TitusServerGroupConfigurationService);
