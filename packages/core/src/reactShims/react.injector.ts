import { StateParams, StateService, UIRouter } from '@uirouter/core';
import { IQService, IRootScopeService, IScope } from 'angular';

import { CacheInitializerService } from '../cache/cacheInitializer.service';
import { ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import { ClusterService } from '../cluster/cluster.service';
import { ImageReader } from '../image/image.reader';
import { InsightFilterStateModel } from '../insight/insightFilterState.model';
import { InstanceTypeService, InstanceWriter } from '../instance';
import { OverrideRegistry } from '../overrideRegistry/override.registry';
import { PageTitleService } from '../pageTitle';
import { ManualJudgmentService } from '../pipeline/config/stages/manualJudgment/manualJudgment.service';
import { ExecutionDetailsSectionService } from '../pipeline/details/executionDetailsSection.service';
import { ExecutionService } from '../pipeline/service/execution.service';
import { InfrastructureSearchService } from '../search/infrastructure/infrastructureSearch.service';
import { SecurityGroupReader } from '../securityGroup/securityGroupReader.service';
import { ServerGroupWriter } from '../serverGroup/serverGroupWriter.service';
import { StateEvents } from './state.events';

import IInjectorService = angular.auto.IInjectorService;

export abstract class ReactInject {
  protected $injector: IInjectorService;

  public abstract initialize($injector: IInjectorService): void;
}

// prettier-ignore
export class CoreReactInject extends ReactInject {
  // weird service because we wrap it
  private wrappedState: StateService;

  public get $state() {
    if (!this.wrappedState) {
      this.wrappedState = this.createStateService();
    }
    return this.wrappedState;
  }

  // Services
  public get $q() { return this.$injector.get('$q') as IQService; }
  public get $rootScope() { return this.$injector.get('$rootScope') as IScope; }
  public get $stateParams() { return this.$injector.get('$stateParams') as StateParams; }
  public get $uiRouter() { return this.$injector.get('$uiRouter') as UIRouter; }
  public get cacheInitializer() { return this.$injector.get('cacheInitializer') as CacheInitializerService; }
  public get clusterService() { return this.$injector.get('clusterService') as ClusterService; }
  public get executionDetailsSectionService() { return this.$injector.get('executionDetailsSectionService') as ExecutionDetailsSectionService; }
  public get executionService() { return this.$injector.get('executionService') as ExecutionService; }
  public get imageReader() { return this.$injector.get('imageReader') as ImageReader; }
  public get infrastructureSearchService() { return this.$injector.get('infrastructureSearchService') as InfrastructureSearchService; }
  public get insightFilterStateModel() { return this.$injector.get('insightFilterStateModel') as InsightFilterStateModel; }
  public get instanceTypeService() { return this.$injector.get('instanceTypeService') as InstanceTypeService; }
  public get instanceWriter() { return this.$injector.get('instanceWriter') as InstanceWriter; }
  public get manualJudgmentService() { return this.$injector.get('manualJudgmentService') as ManualJudgmentService; }
  public get overrideRegistry() { return this.$injector.get('overrideRegistry') as OverrideRegistry; }
  public get pageTitleService() { return this.$injector.get('pageTitleService') as PageTitleService; }
  public get providerServiceDelegate() { return this.$injector.get('providerServiceDelegate') as ProviderServiceDelegate; }
  public get securityGroupReader() { return this.$injector.get('securityGroupReader') as SecurityGroupReader; }
  public get serverGroupWriter() { return this.$injector.get('serverGroupWriter') as ServerGroupWriter; }
  public get stateEvents() { return this.$injector.get('stateEvents') as StateEvents; }

  private createStateService(): StateService {
    const wrappedState = Object.create(this.$injector.get('$state')) as StateService;
    const $rootScope = this.$injector.get('$rootScope') as IRootScopeService;
    const $q = this.$injector.get('$q') as IQService;
    const originalGo = wrappedState.go;

    // Calls $state.go() inside the angularjs digest
    // I forget exactly what this does, but it fixes some angularjs-y digest cycle problem we had once
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

  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }
}

export const ReactInjector: CoreReactInject = new CoreReactInject();
