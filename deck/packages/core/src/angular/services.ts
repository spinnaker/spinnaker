import type { IRootScopeService, ITimeoutService } from 'angular';
import type { IModalService, IModalStackService } from 'angular-ui-bootstrap';

import type { DeckRuntime } from '../bootstrap/DeckRuntime';
import { createDeckRuntime } from '../bootstrap/DeckRuntime';
import { InsightFilterStateModel } from '../insight/insightFilterState.model';
import { overrideRegistry } from '../overrideRegistry/override.registry';

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

class AngularServiceAccessors {
  private runtime = createDeckRuntime();
  private directInsightFilterStateModel: InsightFilterStateModel;

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
  public get insightFilterStateModel() {
    return this.getDirectInsightFilterStateModel();
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
  private getDirectInsightFilterStateModel(): InsightFilterStateModel {
    if (!this.directInsightFilterStateModel) {
      this.directInsightFilterStateModel = new InsightFilterStateModel();
    }

    return this.directInsightFilterStateModel;
  }

  private resetRuntimeServices(): void {
    this.directInsightFilterStateModel = null;
  }
}

export const AngularServices = new AngularServiceAccessors();
