import type { StateParams, StateService, UIRouter } from '@uirouter/core';
import type { IQService, IRootScopeService } from 'angular';
import type { IModalService, IModalStackService } from 'angular-ui-bootstrap';
import { $injector, $q, $rootScope } from 'ngimport';

import type { CacheInitializerService } from '../cache/cacheInitializer.service';
import type { ProviderServiceDelegate } from '../cloudProvider/providerService.delegate';
import type { ClusterService } from '../cluster/cluster.service';
import type { ImageReader } from '../image/image.reader';
import type { InsightFilterStateModel } from '../insight/insightFilterState.model';
import type { InstanceTypeService, InstanceWriter } from '../instance';
import type { LoadBalancerReader } from '../loadBalancer/loadBalancer.read.service';
import type { OverrideRegistry } from '../overrideRegistry/override.registry';
import type { PageTitleService } from '../pageTitle';
import type { ExecutionDetailsSectionService } from '../pipeline/details/executionDetailsSection.service';
import type { ExecutionService } from '../pipeline/service/execution.service';
import type { StateEvents } from '../reactShims/state.events';
import type { InfrastructureSearchService } from '../search/infrastructure/infrastructureSearch.service';
import type { SecurityGroupReader } from '../securityGroup/securityGroupReader.service';
import type { ServerGroupCommandBuilderService } from '../serverGroup/configure/common/serverGroupCommandBuilder.service';
import type { ServerGroupWriter } from '../serverGroup/serverGroupWriter.service';

class AngularServiceAccessors {
  private wrappedState: StateService;

  public get $q() {
    return $q as IQService;
  }
  public get $rootScope() {
    return $rootScope as IRootScopeService;
  }
  public get $state() {
    if (!this.wrappedState) this.wrappedState = this.createStateService();
    return this.wrappedState;
  }
  public get $stateParams() {
    return $injector.get('$stateParams') as StateParams;
  }
  public get $uibModal() {
    return $injector.get('$uibModal') as IModalService;
  }
  public get $uiRouter() {
    return $injector.get('$uiRouter') as UIRouter;
  }
  public get cacheInitializer() {
    return $injector.get('cacheInitializer') as CacheInitializerService;
  }
  public get clusterService() {
    return $injector.get('clusterService') as ClusterService;
  }
  public get executionDetailsSectionService() {
    return $injector.get('executionDetailsSectionService') as ExecutionDetailsSectionService;
  }
  public get executionService() {
    return $injector.get('executionService') as ExecutionService;
  }
  public get imageReader() {
    return $injector.get('imageReader') as ImageReader;
  }
  public get infrastructureSearchService() {
    return $injector.get('infrastructureSearchService') as InfrastructureSearchService;
  }
  public get insightFilterStateModel() {
    return $injector.get('insightFilterStateModel') as InsightFilterStateModel;
  }
  public get instanceTypeService() {
    return $injector.get('instanceTypeService') as InstanceTypeService;
  }
  public get instanceWriter() {
    return $injector.get('instanceWriter') as InstanceWriter;
  }
  public get loadBalancerReader() {
    return $injector.get('loadBalancerReader') as LoadBalancerReader;
  }
  public get modalService() {
    return this.$uibModal;
  }
  public get modalStackService() {
    return $injector.get('$uibModalStack') as IModalStackService;
  }
  public get overrideRegistry() {
    return $injector.get('overrideRegistry') as OverrideRegistry;
  }
  public get pageTitleService() {
    return $injector.get('pageTitleService') as PageTitleService;
  }
  public get providerServiceDelegate() {
    return $injector.get('providerServiceDelegate') as ProviderServiceDelegate;
  }
  public get securityGroupReader() {
    return $injector.get('securityGroupReader') as SecurityGroupReader;
  }
  public get serverGroupCommandBuilder() {
    return $injector.get('serverGroupCommandBuilder') as ServerGroupCommandBuilderService;
  }
  public get serverGroupTransformer() {
    return $injector.get('serverGroupTransformer') as any;
  }
  public get serverGroupWriter() {
    return $injector.get('serverGroupWriter') as ServerGroupWriter;
  }
  public get stateEvents() {
    return $injector.get('stateEvents') as StateEvents;
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
}

export const AngularServices = new AngularServiceAccessors();
