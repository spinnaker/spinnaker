import type { UIRouterReact } from '@uirouter/react';
import type { ILogService, IQService, ITimeoutService } from 'angular';

import type { Application } from '../application';
import { CacheInitializerService } from '../cache/cacheInitializer.service';
import type { DirectProviderServiceDelegate, ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import { ClusterService } from '../cluster/cluster.service';
import { InstanceTypeService } from '../instance';
import { LoadBalancerReader } from '../loadBalancer/loadBalancer.read.service';
import { createLoadBalancerTransformer } from '../loadBalancer/loadBalancer.transformer';
import { ExecutionDetailsSectionService } from '../pipeline/details/executionDetailsSection.service';
import { ExecutionService } from '../pipeline/service/execution.service';
import { InfrastructureSearchService } from '../search/infrastructure/infrastructureSearch.service';
import { SecurityGroupReader } from '../securityGroup/securityGroupReader.service';
import { SecurityGroupTransformerService } from '../securityGroup/securityGroupTransformer.service';
import { ServerGroupCommandBuilderService } from '../serverGroup/configure/common/serverGroupCommandBuilder.service';
import { ServerGroupWriter } from '../serverGroup/serverGroupWriter.service';
import type { CancellableTimeout } from '../utils/cancellableTimeout';

export interface ServerGroupTransformer {
  normalizeServerGroup(serverGroup: any, application: Application): PromiseLike<any>;
  convertServerGroupCommandToDeployConfiguration(base: any): any;
}

export interface RuntimePageTitleService {
  handleRoutingSuccess(config?: { pageTitleMain?: { field?: string; label?: string } }): void;
}

class DirectPageTitleService implements RuntimePageTitleService {
  public handleRoutingSuccess(config: { pageTitleMain?: { field?: string; label?: string } } = {}): void {
    document.title = config.pageTitleMain?.label || 'Spinnaker';
  }
}

export class DeckRuntimeServices {
  private directCacheInitializer: CacheInitializerService;
  private directClusterService: ClusterService;
  private directExecutionDetailsSectionService: ExecutionDetailsSectionService;
  private directExecutionService: ExecutionService;
  private directInfrastructureSearchService: InfrastructureSearchService;
  private directInstanceTypeService: InstanceTypeService;
  private directLoadBalancerReader: LoadBalancerReader;
  private directPageTitleService: RuntimePageTitleService;
  private directSecurityGroupReader: SecurityGroupReader;
  private directServerGroupCommandBuilder: ServerGroupCommandBuilderService;
  private directServerGroupTransformer: ServerGroupTransformer;
  private directServerGroupWriter: ServerGroupWriter;

  constructor(
    private router: UIRouterReact | null,
    private promiseService: IQService,
    private timeoutService: CancellableTimeout,
    private logger: ILogService,
    public readonly providerServiceDelegate: DirectProviderServiceDelegate,
  ) {
    providerServiceDelegate.bindRuntimeServices(this);
  }

  public get cacheInitializer(): CacheInitializerService {
    return (this.directCacheInitializer ||= new CacheInitializerService(
      this.promiseService,
      this.securityGroupReader,
      this.providerServiceDelegate,
    ));
  }

  public get clusterService(): ClusterService {
    return (this.directClusterService ||= new ClusterService(
      this.promiseService,
      this.serverGroupTransformer,
      (this.providerServiceDelegate as unknown) as ProviderServiceDelegate,
    ));
  }

  public get executionDetailsSectionService(): ExecutionDetailsSectionService {
    if (!this.router) {
      throw new Error('Cannot create ExecutionDetailsSectionService before the direct UI Router is initialized');
    }

    return (this.directExecutionDetailsSectionService ||= new ExecutionDetailsSectionService(
      this.router.globals.params,
      this.router.stateService as any,
      (this.timeoutService as unknown) as ITimeoutService,
    ));
  }

  public get executionService(): ExecutionService {
    if (!this.router) {
      throw new Error('Cannot create ExecutionService before the direct UI Router is initialized');
    }

    return (this.directExecutionService ||= new ExecutionService(
      this.promiseService,
      this.router.stateService,
      (this.timeoutService as unknown) as ITimeoutService,
    ));
  }

  public get infrastructureSearchService(): InfrastructureSearchService {
    return (this.directInfrastructureSearchService ||= new InfrastructureSearchService(
      this.promiseService,
      this.providerServiceDelegate,
    ));
  }

  public get instanceTypeService(): InstanceTypeService {
    return (this.directInstanceTypeService ||= new InstanceTypeService(
      (this.providerServiceDelegate as unknown) as ProviderServiceDelegate,
    ));
  }

  public get loadBalancerReader(): LoadBalancerReader {
    return (this.directLoadBalancerReader ||= new LoadBalancerReader(
      this.promiseService,
      createLoadBalancerTransformer(this.providerServiceDelegate),
    ));
  }

  public get pageTitleService(): RuntimePageTitleService {
    return (this.directPageTitleService ||= new DirectPageTitleService());
  }

  public get securityGroupReader(): SecurityGroupReader {
    const providerServiceDelegate = (this.providerServiceDelegate as unknown) as ProviderServiceDelegate;
    const securityGroupTransformer = new SecurityGroupTransformerService(providerServiceDelegate);
    const optionalSecurityGroupTransformer = {
      normalizeSecurityGroup: (securityGroup: any) => {
        const provider = securityGroup.provider || securityGroup.type;
        return provider && this.providerServiceDelegate.hasDelegate(provider, 'securityGroup.transformer')
          ? securityGroupTransformer.normalizeSecurityGroup(securityGroup)
          : Promise.resolve(securityGroup);
      },
    } as SecurityGroupTransformerService;
    return (this.directSecurityGroupReader ||= new SecurityGroupReader(
      this.logger,
      this.promiseService,
      optionalSecurityGroupTransformer,
      providerServiceDelegate,
    ));
  }

  public get serverGroupCommandBuilder(): ServerGroupCommandBuilderService {
    return (this.directServerGroupCommandBuilder ||= new ServerGroupCommandBuilderService(
      (this.providerServiceDelegate as unknown) as ProviderServiceDelegate,
    ));
  }

  public get serverGroupTransformer(): ServerGroupTransformer {
    if (!this.directServerGroupTransformer) {
      this.directServerGroupTransformer = {
        normalizeServerGroup: (serverGroup: any, application: Application) => {
          const provider = serverGroup.provider || serverGroup.type;
          if (!this.providerServiceDelegate.hasDelegate(provider, 'serverGroup.transformer')) {
            return Promise.resolve(serverGroup);
          }

          return this.providerServiceDelegate
            .getDelegate<any>(provider, 'serverGroup.transformer')
            .normalizeServerGroup(serverGroup, application);
        },
        convertServerGroupCommandToDeployConfiguration: (base: any) =>
          this.providerServiceDelegate
            .getDelegate<any>(base.selectedProvider, 'serverGroup.transformer')
            .convertServerGroupCommandToDeployConfiguration(base),
      };
    }

    return this.directServerGroupTransformer;
  }

  public get serverGroupWriter(): ServerGroupWriter {
    return (this.directServerGroupWriter ||= new ServerGroupWriter(this.serverGroupTransformer));
  }

  public dispose(): void {
    this.directCacheInitializer = null;
    this.directClusterService = null;
    this.directExecutionDetailsSectionService = null;
    this.directExecutionService = null;
    this.directInfrastructureSearchService = null;
    this.directInstanceTypeService = null;
    this.directLoadBalancerReader = null;
    this.directPageTitleService = null;
    this.directSecurityGroupReader = null;
    this.directServerGroupCommandBuilder = null;
    this.directServerGroupTransformer = null;
    this.directServerGroupWriter = null;
    this.providerServiceDelegate.dispose();
  }
}
