import type { StateParams, StateService, UIRouter } from '@uirouter/core';
import type { IDeferred, ILogService, IQService, IRootScopeService, ITimeoutService } from 'angular';
import type { IModalService, IModalStackService } from 'angular-ui-bootstrap';
import { $injector, $log, $q, $rootScope, $timeout } from 'ngimport';
import { of as observableOf, Subject } from 'rxjs';
import { switchMap, toArray } from 'rxjs/operators';

import type { Application } from '../application';
import { CacheInitializerService } from '../cache/cacheInitializer.service';
import { CloudProviderRegistry } from '../cloudProvider/CloudProviderRegistry';
import type { ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import { ClusterService } from '../cluster/cluster.service';
import type { ImageReader } from '../image/image.reader';
import { InsightFilterStateModel } from '../insight/insightFilterState.model';
import { InstanceTypeService } from '../instance';
import type { InstanceWriter } from '../instance';
import { LoadBalancerReader } from '../loadBalancer/loadBalancer.read.service';
import { createLoadBalancerTransformer } from '../loadBalancer/loadBalancer.transformer';
import { getDirectRouter } from '../navigation/directRouter';
import type { OverrideRegistry } from '../overrideRegistry/override.registry';
import { overrideRegistry } from '../overrideRegistry/override.registry';
import type { PageTitleService } from '../pageTitle';
import { ExecutionDetailsSectionService } from '../pipeline/details/executionDetailsSection.service';
import { ExecutionService } from '../pipeline/service/execution.service';
import type { StateEvents } from '../reactShims/state.events';
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

const directQ = Object.assign(
  ((<T>(resolver: (resolve: (value: T | PromiseLike<T>) => void, reject: (reason?: unknown) => void) => void) => {
    return new Promise<T>(resolver);
  }) as unknown) as IQService,
  {
    defer: () => {
      let resolve: (value: unknown) => void;
      let reject: (reason?: unknown) => void;
      const promise = new Promise((promiseResolve, promiseReject) => {
        resolve = promiseResolve;
        reject = promiseReject;
      });

      return { promise, resolve, reject, notify: () => undefined };
    },
    all: (promises: Array<PromiseLike<unknown>>) => Promise.all(promises),
    reject: (reason?: unknown) => Promise.reject(reason),
    resolve: (value?: unknown) => Promise.resolve(value),
    when: (value: unknown) => Promise.resolve(value),
  },
) as IQService;

const directTimeout = Object.assign(
  (((fnOrDelay?: (() => unknown) | number, delay?: number) => {
    const hasCallback = typeof fnOrDelay === 'function';
    const timeoutDelay = hasCallback ? delay : fnOrDelay;
    let timeoutId: ReturnType<typeof setTimeout> | undefined;
    const promise = new Promise((resolve) => {
      timeoutId = setTimeout(() => resolve(hasCallback ? fnOrDelay() : undefined), timeoutDelay || 0);
    });
    if (timeoutId) {
      (promise as any).timeoutId = timeoutId;
    }
    return promise;
  }) as unknown) as ITimeoutService,
  {
    cancel: (promise?: PromiseLike<unknown> & { timeoutId?: ReturnType<typeof setTimeout> }) => {
      if (promise?.timeoutId) {
        clearTimeout(promise.timeoutId);
      }
      return true;
    },
  },
);

const noopLog = (): void => undefined;

const directLog = ({
  debug: noopLog,
  error: (...args: unknown[]) => console.error(...args),
  info: noopLog,
  log: noopLog,
  warn: (...args: unknown[]) => console.warn(...args),
} as unknown) as ILogService;

const directProviderServiceDelegate = ({
  hasDelegate: (provider: string, serviceKey: string): boolean => {
    return typeof CloudProviderRegistry.getValue(provider, serviceKey) === 'function';
  },
  getDelegate: <T>(provider: string, serviceKey: string): T => {
    const ServiceClass = CloudProviderRegistry.getValue(provider, serviceKey);
    if (typeof ServiceClass !== 'function') {
      throw new Error('No "' + serviceKey + '" service found for provider "' + provider + '"');
    }
    return new ServiceClass(directQ) as T;
  },
} as unknown) as ProviderServiceDelegate;

const directServerGroupTransformer = {
  normalizeServerGroup: (serverGroup: any, application: Application) => {
    const provider = serverGroup.provider || serverGroup.type;
    if (!directProviderServiceDelegate.hasDelegate(provider, 'serverGroup.transformer')) {
      return Promise.resolve(serverGroup);
    }

    return directProviderServiceDelegate
      .getDelegate<any>(provider, 'serverGroup.transformer')
      .normalizeServerGroup(serverGroup, application);
  },
  convertServerGroupCommandToDeployConfiguration: (base: any) => {
    return directProviderServiceDelegate
      .getDelegate<any>(base.selectedProvider, 'serverGroup.transformer')
      .convertServerGroupCommandToDeployConfiguration(base);
  },
};

const directServerGroupCommandBuilder = ({
  buildNewServerGroupCommand: (application: Application, provider: string, options: any) => {
    return directProviderServiceDelegate
      .getDelegate<any>(provider, 'serverGroup.commandBuilder')
      .buildNewServerGroupCommand(application, options);
  },
  buildServerGroupCommandFromExisting: (application: Application, serverGroup: any, mode?: string) => {
    return directProviderServiceDelegate
      .getDelegate<any>(serverGroup.type, 'serverGroup.commandBuilder')
      .buildServerGroupCommandFromExisting(application, serverGroup, mode);
  },
  buildNewServerGroupCommandForPipeline: (provider: string, currentStage: any, pipeline: any) => {
    return directProviderServiceDelegate
      .getDelegate<any>(provider, 'serverGroup.commandBuilder')
      .buildNewServerGroupCommandForPipeline(currentStage, pipeline);
  },
  buildServerGroupCommandFromPipeline: (application: Application, cluster: any, currentStage: any, pipeline: any) => {
    return directProviderServiceDelegate
      .getDelegate<any>(cluster.provider, 'serverGroup.commandBuilder')
      .buildServerGroupCommandFromPipeline(application, cluster, currentStage, pipeline);
  },
} as unknown) as ServerGroupCommandBuilderService;

const directRootScope = ({
  routing: false,
  $apply: (fn?: () => void) => fn?.(),
  $applyAsync: (fn: () => void) => setTimeout(fn, 0),
  $broadcast: () => ({ defaultPrevented: false, preventDefault: (): void => undefined }),
  $new: () => directRootScope,
  $on: () => (): void => undefined,
  $watch: () => (): void => undefined,
} as unknown) as IRootScopeService;

const directInterpolate = (template: string) => (context: Record<string, any>): string => {
  return template.replace(/{{\s*([^}]+?)\s*}}/g, (_match, expression: string) => {
    const value = expression
      .split('.')
      .map((part) => part.trim())
      .reduce((current, part) => current?.[part], context);

    return value === undefined || value === null ? '' : String(value);
  });
};

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
  private wrappedState: StateService;
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
  private directStateEvents: StateEvents;

  public get $q() {
    return ($q || directQ) as IQService;
  }
  public get $log() {
    try {
      return ($log || $injector.get('$log') || directLog) as ILogService;
    } catch (_error) {
      return directLog;
    }
  }
  public get $rootScope() {
    try {
      return ($rootScope || directRootScope) as IRootScopeService;
    } catch (_error) {
      return directRootScope;
    }
  }
  public get $timeout() {
    try {
      return ($timeout || $injector.get('$timeout') || directTimeout) as ITimeoutService;
    } catch (_error) {
      return directTimeout;
    }
  }
  public get $state() {
    if (!this.wrappedState) {
      try {
        this.wrappedState = this.createStateService();
      } catch (error) {
        const directRouter = getDirectRouter();
        if (directRouter) {
          return directRouter.stateService as StateService;
        }
        throw error;
      }
    }
    return this.wrappedState;
  }
  public get $stateParams() {
    try {
      return $injector.get('$stateParams') as StateParams;
    } catch (_error) {
      const directRouter = getDirectRouter();
      return (directRouter?.globals.params || {}) as StateParams;
    }
  }
  public get $interpolate() {
    try {
      return $injector.get('$interpolate') as typeof directInterpolate;
    } catch (_error) {
      return directInterpolate;
    }
  }
  public get $uibModal() {
    try {
      const modalService = $injector.get('$uibModal') as IModalService;
      return modalService?.open ? modalService : directModalService;
    } catch (_error) {
      return directModalService;
    }
  }
  public get $uiRouter() {
    try {
      return $injector.get('$uiRouter') as UIRouter;
    } catch (_error) {
      const directRouter = getDirectRouter();
      if (directRouter) {
        return (directRouter as unknown) as UIRouter;
      }

      const noopSubscription = { unsubscribe: () => {} };
      type NoopObservable = {
        subscribe: () => typeof noopSubscription;
        pipe: (..._operators: unknown[]) => NoopObservable;
      };
      const noopObservable: NoopObservable = {
        subscribe: () => noopSubscription,
        pipe: () => noopObservable,
      };

      return ({
        globals: {
          current: { name: '' },
          params: {},
          params$: noopObservable,
          start$: noopObservable,
          success$: noopObservable,
        },
        transitionService: {
          onBefore: () => () => {},
          onStart: () => () => {},
          onSuccess: () => () => {},
        },
      } as unknown) as UIRouter;
    }
  }
  public get cacheInitializer() {
    if (!$injector || !$injector.has('cacheInitializer')) {
      return this.getDirectCacheInitializer();
    }

    return $injector.get('cacheInitializer') as CacheInitializerService;
  }
  public get clusterService() {
    try {
      return $injector.get('clusterService') as ClusterService;
    } catch (_error) {
      return this.getDirectClusterService();
    }
  }
  public get executionDetailsSectionService() {
    try {
      return $injector.get('executionDetailsSectionService') as ExecutionDetailsSectionService;
    } catch (_error) {
      return this.getDirectExecutionDetailsSectionService();
    }
  }
  public get executionService() {
    try {
      return $injector.get('executionService') as ExecutionService;
    } catch (_error) {
      return this.getDirectExecutionService();
    }
  }
  public get imageReader() {
    return $injector.get('imageReader') as ImageReader;
  }
  public get infrastructureSearchService(): DirectInfrastructureSearchService {
    try {
      return $injector.get('infrastructureSearchService') as DirectInfrastructureSearchService;
    } catch (_error) {
      return this.getDirectInfrastructureSearchService();
    }
  }
  public get insightFilterStateModel() {
    try {
      return $injector.get('insightFilterStateModel') as InsightFilterStateModel;
    } catch (_error) {
      return this.getDirectInsightFilterStateModel();
    }
  }
  public get instanceTypeService() {
    try {
      return $injector.get('instanceTypeService') as InstanceTypeService;
    } catch (_error) {
      return this.getDirectInstanceTypeService();
    }
  }
  public get instanceWriter() {
    return $injector.get('instanceWriter') as InstanceWriter;
  }
  public get loadBalancerReader() {
    if (!$injector || !$injector.has('loadBalancerReader')) {
      return this.getDirectLoadBalancerReader();
    }

    return $injector.get('loadBalancerReader') as LoadBalancerReader;
  }
  public get modalService() {
    return this.$uibModal;
  }
  public get modalStackService() {
    try {
      return $injector.get('$uibModalStack') as IModalStackService;
    } catch (_error) {
      return directModalStackService;
    }
  }
  public get overrideRegistry() {
    try {
      return $injector.get('overrideRegistry') as OverrideRegistry;
    } catch (_error) {
      return overrideRegistry;
    }
  }
  public get pageTitleService() {
    try {
      return $injector.get('pageTitleService') as PageTitleService;
    } catch (_error) {
      return this.directPageTitleService as PageTitleService;
    }
  }
  public get providerServiceDelegate() {
    try {
      return $injector.get('providerServiceDelegate') as ProviderServiceDelegate;
    } catch (_error) {
      return directProviderServiceDelegate;
    }
  }
  public get securityGroupReader() {
    try {
      return $injector.get('securityGroupReader') as SecurityGroupReader;
    } catch (_error) {
      return this.getDirectSecurityGroupReader();
    }
  }
  public get serverGroupCommandBuilder() {
    try {
      return $injector.get('serverGroupCommandBuilder') as ServerGroupCommandBuilderService;
    } catch (_error) {
      return directServerGroupCommandBuilder;
    }
  }
  public get serverGroupTransformer() {
    try {
      return $injector.get('serverGroupTransformer') as any;
    } catch (_error) {
      return directServerGroupTransformer;
    }
  }
  public get serverGroupWriter() {
    if (!$injector || !$injector.has('serverGroupWriter')) {
      return this.getDirectServerGroupWriter();
    }

    return $injector.get('serverGroupWriter') as ServerGroupWriter;
  }
  public get stateEvents() {
    try {
      return $injector.get('stateEvents') as StateEvents;
    } catch (_error) {
      return this.getDirectStateEvents();
    }
  }

  public has(serviceName: string): boolean {
    try {
      if ($injector.has(serviceName)) {
        return true;
      }
    } catch (_error) {
      // Fall through to direct runtime services.
    }

    return serviceName === '$uiRouter' && getDirectRouter() !== null;
  }

  private createStateService(): StateService {
    const wrappedState = Object.create($injector.get('$state')) as StateService;
    const originalGo = wrappedState.go;

    wrappedState.go = function () {
      const args = arguments;
      const deferred = Object.create($q.defer());
      const { promise } = deferred;
      promise.transition = null;
      promise.catch(() => {});
      $rootScope.$applyAsync(() => {
        const originalPromise = originalGo.apply(this, args);
        promise.transition = originalPromise.transition;
        originalPromise.then(deferred.resolve, deferred.reject);
      });
      return promise;
    };
    return wrappedState;
  }

  private getDirectCacheInitializer(): CacheInitializerService {
    if (!this.directCacheInitializer) {
      this.directCacheInitializer = new CacheInitializerService(
        directQ,
        this.getDirectSecurityGroupReader(),
        directProviderServiceDelegate,
      );
    }

    return this.directCacheInitializer;
  }

  private getDirectInfrastructureSearchService(): DirectInfrastructureSearchService {
    if (!this.directInfrastructureSearchService) {
      this.directInfrastructureSearchService = new DirectInfrastructureSearchService(
        directQ,
        directProviderServiceDelegate,
      );
    }

    return this.directInfrastructureSearchService;
  }

  private getDirectLoadBalancerReader(): LoadBalancerReader {
    if (!this.directLoadBalancerReader) {
      this.directLoadBalancerReader = new LoadBalancerReader(
        directQ,
        createLoadBalancerTransformer(directProviderServiceDelegate),
      );
    }

    return this.directLoadBalancerReader;
  }

  private getDirectStateEvents(): StateEvents {
    if (!this.directStateEvents) {
      const stateChangeSuccess = new Subject();
      const locationChangeSuccess = new Subject<string>();
      this.directStateEvents = ({ stateChangeSuccess, locationChangeSuccess } as unknown) as StateEvents;

      const directRouter = getDirectRouter();
      directRouter?.transitionService.onSuccess({}, (transition: any) => {
        stateChangeSuccess.next({
          to: transition.to(),
          toParams: transition.params('to'),
          from: transition.from(),
          fromParams: transition.params('from'),
        });
        locationChangeSuccess.next(window.location.href);
      });
    }

    return this.directStateEvents;
  }

  private getDirectClusterService(): ClusterService {
    if (!this.directClusterService) {
      this.directClusterService = new ClusterService(
        directQ,
        directServerGroupTransformer,
        directProviderServiceDelegate,
      );
    }

    return this.directClusterService;
  }

  private getDirectExecutionService(): ExecutionService {
    if (!this.directExecutionService) {
      this.directExecutionService = new ExecutionService(directQ, this.$state, directTimeout);
    }

    return this.directExecutionService;
  }

  private getDirectExecutionDetailsSectionService(): ExecutionDetailsSectionService {
    if (!this.directExecutionDetailsSectionService) {
      this.directExecutionDetailsSectionService = new ExecutionDetailsSectionService(
        this.$stateParams,
        this.$state as any,
        this.$timeout,
      );
    }

    return this.directExecutionDetailsSectionService;
  }

  private getDirectSecurityGroupReader(): SecurityGroupReader {
    if (!this.directSecurityGroupReader) {
      this.directSecurityGroupReader = new SecurityGroupReader(
        directLog,
        directQ,
        new SecurityGroupTransformerService(directProviderServiceDelegate),
        directProviderServiceDelegate,
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
      this.directInstanceTypeService = new InstanceTypeService(directProviderServiceDelegate);
    }

    return this.directInstanceTypeService;
  }

  private getDirectInsightFilterStateModel(): InsightFilterStateModel {
    if (!this.directInsightFilterStateModel) {
      this.directInsightFilterStateModel = new InsightFilterStateModel();
    }

    return this.directInsightFilterStateModel;
  }
}

export const AngularServices = new AngularServiceAccessors();
