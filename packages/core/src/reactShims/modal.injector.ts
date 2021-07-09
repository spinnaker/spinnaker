import IInjectorService = angular.auto.IInjectorService;

import { IModalService, IModalStackService } from 'angular-ui-bootstrap';

import { ReactInject } from './react.injector';

// prettier-ignore
export class CoreModalInject extends ReactInject {
  // Services
  public get modalService(): IModalService { return this.$injector.get('$uibModal') as IModalService; }
  public get modalStackService(): IModalStackService { return this.$injector.get('$uibModalStack') as IModalStackService; }

  public initialize($injector: IInjectorService) {
    this.$injector = $injector;
  }
}

export const ModalInjector: CoreModalInject = new CoreModalInject();
