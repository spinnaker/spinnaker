import type { Rejection } from '@uirouter/core';
import { RejectType } from '@uirouter/core';
import type { UIRouterReact } from '@uirouter/react';

import type { Application } from '../application';
import { CacheInitializerService } from '../cache/cacheInitializer.service';
import type { ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
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
import type { DiagnosticLogger } from '../utils/diagnosticLogger';
import type { PromiseService } from '../utils/nativePromiseService';

export interface ServerGroupTransformer {
  normalizeServerGroup(serverGroup: any, application: Application): PromiseLike<any>;
  convertServerGroupCommandToDeployConfiguration(base: any): any;
}

interface RuntimePageTitleData {
  pageTitleMain?: { field?: string; label?: string };
  pageTitleSection?: { title?: string };
  pageTitleDetails?: { title?: string; nameParam?: string; accountParam?: string; regionParam?: string };
}

export interface RuntimePageTitleConfig extends RuntimePageTitleData {
  data?: RuntimePageTitleData;
}

export interface RuntimePageTitleService {
  handleRoutingStart(transition: object): void;
  handleRoutingError(rejection: Pick<Rejection, 'type'>, transition: object): void;
  handleRoutingSuccess(config?: RuntimePageTitleConfig, transition?: object): void;
  dispose(): void;
}

class DirectPageTitleService implements RuntimePageTitleService {
  private activeTransition: object | null = null;
  private disposed = false;
  private previousPageTitle = 'Spinnaker';

  constructor(private router: UIRouterReact | null) {}

  public handleRoutingStart(transition: object): void {
    if (this.disposed) {
      return;
    }
    if (!this.activeTransition) {
      this.previousPageTitle = document.title;
    }
    this.activeTransition = transition;
    document.title = 'Spinnaker: Loading...';
  }

  public handleRoutingError(rejection: Pick<Rejection, 'type'>, transition: object): void {
    if (this.disposed || rejection.type === RejectType.SUPERSEDED || transition !== this.activeTransition) {
      return;
    }
    this.activeTransition = null;
    document.title = rejection.type === RejectType.ABORTED ? this.previousPageTitle : 'Spinnaker: Error';
  }

  public handleRoutingSuccess(config: RuntimePageTitleConfig = {}, transition?: object): void {
    if (this.disposed || (transition ? transition !== this.activeTransition : Boolean(this.activeTransition))) {
      return;
    }
    this.activeTransition = null;
    const pageData = config.data || config;
    const params = (this.router?.globals.params || {}) as Record<string, any>;
    const mainConfig = pageData.pageTitleMain;
    const main = mainConfig?.label || (mainConfig?.field && params[mainConfig.field]) || 'Spinnaker';
    const resolveStateParams = (stateConfig?: RuntimePageTitleData['pageTitleDetails']): string => {
      if (!stateConfig) {
        return '';
      }

      const { title, nameParam, accountParam, regionParam } = stateConfig;
      let result = title;
      if (nameParam) {
        result += ': ' + params[nameParam];
      }
      if (accountParam || regionParam) {
        const location =
          accountParam && regionParam
            ? `${params[accountParam]}:${params[regionParam]}`
            : params[accountParam || regionParam];
        result += ` (${location})`;
      }
      return result;
    };

    document.title = [
      main,
      resolveStateParams(pageData.pageTitleSection),
      resolveStateParams(pageData.pageTitleDetails),
    ]
      .filter(Boolean)
      .join(' · ');
  }

  public dispose(): void {
    this.disposed = true;
    this.activeTransition = null;
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
    private promiseService: PromiseService,
    private timeoutService: CancellableTimeout,
    private logger: DiagnosticLogger,
    public readonly providerServiceDelegate: ProviderServiceDelegate,
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
      this.providerServiceDelegate,
    ));
  }

  public get executionDetailsSectionService(): ExecutionDetailsSectionService {
    if (!this.router) {
      throw new Error('Cannot create ExecutionDetailsSectionService before the direct UI Router is initialized');
    }

    return (this.directExecutionDetailsSectionService ||= new ExecutionDetailsSectionService(
      this.router.globals.params,
      this.router.stateService,
      this.timeoutService,
    ));
  }

  public get executionService(): ExecutionService {
    if (!this.router) {
      throw new Error('Cannot create ExecutionService before the direct UI Router is initialized');
    }

    return (this.directExecutionService ||= new ExecutionService(this.router.stateService, this.timeoutService));
  }

  public get infrastructureSearchService(): InfrastructureSearchService {
    return (this.directInfrastructureSearchService ||= new InfrastructureSearchService(this.providerServiceDelegate));
  }

  public get instanceTypeService(): InstanceTypeService {
    return (this.directInstanceTypeService ||= new InstanceTypeService(this.providerServiceDelegate));
  }

  public get loadBalancerReader(): LoadBalancerReader {
    return (this.directLoadBalancerReader ||= new LoadBalancerReader(
      this.promiseService,
      createLoadBalancerTransformer(this.providerServiceDelegate),
    ));
  }

  public get pageTitleService(): RuntimePageTitleService {
    return (this.directPageTitleService ||= new DirectPageTitleService(this.router));
  }

  public get securityGroupReader(): SecurityGroupReader {
    const securityGroupTransformer = new SecurityGroupTransformerService(this.providerServiceDelegate);
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
      this.providerServiceDelegate,
    ));
  }

  public get serverGroupCommandBuilder(): ServerGroupCommandBuilderService {
    return (this.directServerGroupCommandBuilder ||= new ServerGroupCommandBuilderService(
      this.providerServiceDelegate,
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
    this.directPageTitleService?.dispose();
    this.directSecurityGroupReader = null;
    this.directServerGroupCommandBuilder = null;
    this.directServerGroupTransformer = null;
    this.directServerGroupWriter = null;
    this.providerServiceDelegate.dispose();
  }
}
