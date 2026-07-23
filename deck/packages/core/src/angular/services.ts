import type { IDeferred, IQService, IRootScopeService, ITimeoutService } from 'angular';
import type { IModalService, IModalStackService } from 'angular-ui-bootstrap';
import { of as observableOf, Subject } from 'rxjs';
import { switchMap, toArray } from 'rxjs/operators';

import type { Application } from '../application';
import type { DeckRuntime } from '../bootstrap/DeckRuntime';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { CacheInitializerService } from '../cache/cacheInitializer.service';
import type { ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import { ClusterService } from '../cluster/cluster.service';
import { InsightFilterStateModel } from '../insight/insightFilterState.model';
import { InstanceTypeService } from '../instance';
import { LoadBalancerReader } from '../loadBalancer/loadBalancer.read.service';
import { createLoadBalancerTransformer } from '../loadBalancer/loadBalancer.transformer';
import { getDirectRouter } from '../navigation/directRouter';
import { overrideRegistry } from '../overrideRegistry/override.registry';
import type { PageTitleService } from '../pageTitle';
import { ExecutionDetailsSectionService } from '../pipeline/details/executionDetailsSection.service';
import { ExecutionService } from '../pipeline/service/execution.service';
import type {
  IProviderResultFormatter,
  ISearchResultFormatter,
  ISearchResultSet,
} from '../search/infrastructure/infrastructureSearch.service';
import { InfrastructureSearchServiceV2 } from '../search/infrastructure/infrastructureSearchV2.service';
import type { ISearchResult } from '../search/search.service';
import { SearchStatus } from '../search/searchResult/SearchStatus';
import type { SearchResultType } from '../search/searchResult/searchResultType';
import { searchResultTypeRegistry } from '../search/searchResult/searchResultType.registry';
import { SecurityGroupReader } from '../securityGroup/securityGroupReader.service';
import { SecurityGroupTransformerService } from '../securityGroup/securityGroupTransformer.service';
import type { ServerGroupCommandBuilderService } from '../serverGroup/configure/common/serverGroupCommandBuilder.service';
import { ServerGroupWriter } from '../serverGroup/serverGroupWriter.service';

const directServerGroupTransformer = {
  normalizeServerGroup: (serverGroup: any, application: Application) => {
    const provider = serverGroup.provider || serverGroup.type;
    if (!AngularServices.providerServiceDelegate.hasDelegate(provider, 'serverGroup.transformer')) {
      return Promise.resolve(serverGroup);
    }

    return AngularServices.providerServiceDelegate
      .getDelegate<any>(provider, 'serverGroup.transformer')
      .normalizeServerGroup(serverGroup, application);
  },
  convertServerGroupCommandToDeployConfiguration: (base: any) => {
    return AngularServices.providerServiceDelegate
      .getDelegate<any>(base.selectedProvider, 'serverGroup.transformer')
      .convertServerGroupCommandToDeployConfiguration(base);
  },
};

const directServerGroupCommandBuilder = ({
  buildNewServerGroupCommand: (application: Application, provider: string, options: any) => {
    return AngularServices.providerServiceDelegate
      .getDelegate<any>(provider, 'serverGroup.commandBuilder')
      .buildNewServerGroupCommand(application, options);
  },
  buildServerGroupCommandFromExisting: (application: Application, serverGroup: any, mode?: string) => {
    return AngularServices.providerServiceDelegate
      .getDelegate<any>(serverGroup.type, 'serverGroup.commandBuilder')
      .buildServerGroupCommandFromExisting(application, serverGroup, mode);
  },
  buildNewServerGroupCommandForPipeline: (provider: string, currentStage: any, pipeline: any) => {
    return AngularServices.providerServiceDelegate
      .getDelegate<any>(provider, 'serverGroup.commandBuilder')
      .buildNewServerGroupCommandForPipeline(currentStage, pipeline);
  },
  buildServerGroupCommandFromPipeline: (application: Application, cluster: any, currentStage: any, pipeline: any) => {
    return AngularServices.providerServiceDelegate
      .getDelegate<any>(cluster.provider, 'serverGroup.commandBuilder')
      .buildServerGroupCommandFromPipeline(application, cluster, currentStage, pipeline);
  },
} as unknown) as ServerGroupCommandBuilderService;

const directRootScope = ({
  routing: false,
  $apply: (fn?: () => void) => fn?.(),
  $applyAsync: (fn: () => void) => AngularServices.$timeout(fn, 0),
  $broadcast: () => ({ defaultPrevented: false, preventDefault: (): void => undefined }),
  $new: () => directRootScope,
  $on: () => (): void => undefined,
  $watch: () => (): void => undefined,
} as unknown) as IRootScopeService;

const directModalService = ({
  open: () => ({ result: Promise.reject() }),
} as unknown) as IModalService;

const directModalStackService = ({
  dismissAll: (): void => undefined,
} as unknown) as IModalStackService;

class DirectInfrastructureSearcher {
  private deferred: IDeferred<ISearchResultSet[]>;
  private querySubject: Subject<string> = new Subject<string>();

  constructor(private $q: IQService, private providerServiceDelegate: ProviderServiceDelegate) {
    this.querySubject
      .pipe(
        switchMap((query: string) => {
          if (!query || query.trim() === '') {
            const fallbackResults = searchResultTypeRegistry
              .getAll()
              .map((type) => ({ type, results: [], status: SearchStatus.INITIAL } as ISearchResultSet));
            return observableOf(fallbackResults);
          }

          return InfrastructureSearchServiceV2.search({ key: query }).pipe(toArray());
        }),
      )
      .subscribe((result: ISearchResultSet[]) => {
        this.deferred.resolve(result);
      });
  }

  public query(q: string): PromiseLike<ISearchResultSet[]> {
    this.deferred = this.$q.defer();
    this.querySubject.next(q);
    return this.deferred.promise;
  }

  public getCategoryConfig(category: string): SearchResultType {
    return searchResultTypeRegistry.get(category);
  }

  public formatRouteResult(category: string, entry: ISearchResult): PromiseLike<string> {
    return this.formatResult(category, entry, true);
  }

  private formatResult(category: string, entry: ISearchResult, fromRoute = false): PromiseLike<string> {
    const type = searchResultTypeRegistry.get(category);
    if (!type) {
      return this.$q.when('') as PromiseLike<string>;
    }

    let formatter: ISearchResultFormatter = type.displayFormatter;
    if (this.providerServiceDelegate.hasDelegate(entry.provider, 'search.resultFormatter')) {
      const providerFormatter: IProviderResultFormatter = this.providerServiceDelegate.getDelegate<
        IProviderResultFormatter
      >(entry.provider, 'search.resultFormatter');
      if (providerFormatter[category]) {
        formatter = providerFormatter[category];
      }
    }
    return this.$q.when(formatter(entry, fromRoute)) as PromiseLike<string>;
  }
}

class DirectInfrastructureSearchService {
  constructor(private $q: IQService, private providerServiceDelegate: ProviderServiceDelegate) {}

  public getSearcher(): DirectInfrastructureSearcher {
    return new DirectInfrastructureSearcher(this.$q, this.providerServiceDelegate);
  }
}

class DirectPageTitleService {
  public handleRoutingSuccess(config: { pageTitleMain?: { field?: string; label?: string } } = {}): void {
    document.title = config.pageTitleMain?.label || 'Spinnaker';
  }
}

class AngularServiceAccessors {
  private runtime = createDeckRuntime();
  private directCacheInitializer: CacheInitializerService;
  private directClusterService: ClusterService;
  private directExecutionDetailsSectionService: ExecutionDetailsSectionService;
  private directExecutionService: ExecutionService;
  private directInfrastructureSearchService: DirectInfrastructureSearchService;
  private directInsightFilterStateModel: InsightFilterStateModel;
  private directInstanceTypeService: InstanceTypeService;
  private directLoadBalancerReader: LoadBalancerReader;
  private directPageTitleService = new DirectPageTitleService();
  private directSecurityGroupReader: SecurityGroupReader;
  private directServerGroupWriter: ServerGroupWriter;

  public bindRuntime(runtime: DeckRuntime): void {
    if (this.runtime === runtime) {
      return;
    }

    this.runtime.dispose();
    this.resetRuntimeServices();
    this.runtime = runtime;
  }

  public releaseRuntime(runtime: DeckRuntime): void {
    if (this.runtime !== runtime) {
      return;
    }

    this.resetRuntimeServices();
    this.runtime = createDeckRuntime();
  }

  public get $q() {
    return this.runtime.promiseService;
  }
  public get $log() {
    return this.runtime.logger;
  }
  public get $rootScope() {
    return directRootScope;
  }
  public get $timeout() {
    return (this.runtime.timeoutService as unknown) as ITimeoutService;
  }
  public get $interpolate() {
    return this.runtime.interpolate;
  }
  public get $uibModal() {
    return directModalService;
  }
  public get cacheInitializer() {
    return this.getDirectCacheInitializer();
  }
  public get clusterService() {
    return this.getDirectClusterService();
  }
  public get executionDetailsSectionService() {
    return this.getDirectExecutionDetailsSectionService();
  }
  public get executionService() {
    return this.getDirectExecutionService();
  }
  public get infrastructureSearchService(): DirectInfrastructureSearchService {
    return this.getDirectInfrastructureSearchService();
  }
  public get insightFilterStateModel() {
    return this.getDirectInsightFilterStateModel();
  }
  public get instanceTypeService() {
    return this.getDirectInstanceTypeService();
  }
  public get loadBalancerReader() {
    return this.getDirectLoadBalancerReader();
  }
  public get modalService() {
    return this.$uibModal;
  }
  public get modalStackService() {
    return directModalStackService;
  }
  public get overrideRegistry() {
    return overrideRegistry;
  }
  public get pageTitleService() {
    return this.directPageTitleService as PageTitleService;
  }
  public get providerServiceDelegate() {
    return (this.runtime.providerServiceDelegate as unknown) as ProviderServiceDelegate;
  }
  public get securityGroupReader() {
    return this.getDirectSecurityGroupReader();
  }
  public get serverGroupCommandBuilder() {
    return directServerGroupCommandBuilder;
  }
  public get serverGroupTransformer() {
    return directServerGroupTransformer;
  }
  public get serverGroupWriter() {
    return this.getDirectServerGroupWriter();
  }
  private getDirectCacheInitializer(): CacheInitializerService {
    if (!this.directCacheInitializer) {
      this.directCacheInitializer = new CacheInitializerService(
        this.$q,
        this.getDirectSecurityGroupReader(),
        this.providerServiceDelegate,
      );
    }

    return this.directCacheInitializer;
  }

  private getDirectInfrastructureSearchService(): DirectInfrastructureSearchService {
    if (!this.directInfrastructureSearchService) {
      this.directInfrastructureSearchService = new DirectInfrastructureSearchService(
        this.$q,
        this.providerServiceDelegate,
      );
    }

    return this.directInfrastructureSearchService;
  }

  private getDirectLoadBalancerReader(): LoadBalancerReader {
    if (!this.directLoadBalancerReader) {
      this.directLoadBalancerReader = new LoadBalancerReader(
        this.$q,
        createLoadBalancerTransformer(this.providerServiceDelegate),
      );
    }

    return this.directLoadBalancerReader;
  }

  private getDirectClusterService(): ClusterService {
    if (!this.directClusterService) {
      this.directClusterService = new ClusterService(
        this.$q,
        directServerGroupTransformer,
        this.providerServiceDelegate,
      );
    }

    return this.directClusterService;
  }

  private getDirectExecutionService(): ExecutionService {
    if (!this.directExecutionService) {
      const router = getDirectRouter();
      if (!router) {
        throw new Error('Cannot create ExecutionService before the direct UI Router is initialized');
      }
      this.directExecutionService = new ExecutionService(this.$q, router.stateService, this.$timeout);
    }

    return this.directExecutionService;
  }

  private getDirectExecutionDetailsSectionService(): ExecutionDetailsSectionService {
    if (!this.directExecutionDetailsSectionService) {
      const router = getDirectRouter();
      if (!router) {
        throw new Error('Cannot create ExecutionDetailsSectionService before the direct UI Router is initialized');
      }
      this.directExecutionDetailsSectionService = new ExecutionDetailsSectionService(
        router.globals.params,
        router.stateService as any,
        this.$timeout,
      );
    }

    return this.directExecutionDetailsSectionService;
  }

  private getDirectSecurityGroupReader(): SecurityGroupReader {
    if (!this.directSecurityGroupReader) {
      this.directSecurityGroupReader = new SecurityGroupReader(
        this.$log,
        this.$q,
        new SecurityGroupTransformerService(this.providerServiceDelegate),
        this.providerServiceDelegate,
      );
    }

    return this.directSecurityGroupReader;
  }

  private getDirectServerGroupWriter(): ServerGroupWriter {
    if (!this.directServerGroupWriter) {
      this.directServerGroupWriter = new ServerGroupWriter(directServerGroupTransformer);
    }

    return this.directServerGroupWriter;
  }

  private getDirectInstanceTypeService(): InstanceTypeService {
    if (!this.directInstanceTypeService) {
      this.directInstanceTypeService = new InstanceTypeService(this.providerServiceDelegate);
    }

    return this.directInstanceTypeService;
  }

  private getDirectInsightFilterStateModel(): InsightFilterStateModel {
    if (!this.directInsightFilterStateModel) {
      this.directInsightFilterStateModel = new InsightFilterStateModel();
    }

    return this.directInsightFilterStateModel;
  }

  private resetRuntimeServices(): void {
    this.directCacheInitializer = null;
    this.directClusterService = null;
    this.directExecutionDetailsSectionService = null;
    this.directExecutionService = null;
    this.directInfrastructureSearchService = null;
    this.directInstanceTypeService = null;
    this.directLoadBalancerReader = null;
    this.directSecurityGroupReader = null;
    this.directServerGroupWriter = null;
  }
}

export const AngularServices = new AngularServiceAccessors();
