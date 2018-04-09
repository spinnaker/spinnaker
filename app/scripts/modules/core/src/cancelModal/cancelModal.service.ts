import { module } from 'angular';
import { IModalService, IModalSettings } from 'angular-ui-bootstrap';
import { CANCEL_MODAL_CONTROLLER } from './cancelModal.controller';

export interface ICancelModalParams {
  body?: string;
  buttonText: string;
  cancelButtonText?: string;
  header: string;
  submitMethod: (reason: string, force?: boolean) => ng.IPromise<any>;
}

export class CancelModalService {
  private defaults: Partial<ICancelModalParams> = {
    cancelButtonText: 'Cancel',
  };

  public constructor(private $uibModal: IModalService, private $sce: ng.ISCEService) {}

  public confirm(params: ICancelModalParams): ng.IPromise<any> {
    const extendedParams: ICancelModalParams = Object.assign({}, this.defaults, params);

    if (extendedParams.body) {
      extendedParams.body = this.$sce.trustAsHtml(extendedParams.body);
    }

    const modalArgs: IModalSettings = {
      templateUrl: require('./cancel.html'),
      controller: 'cancelModalCtrl',
      controllerAs: 'ctrl',
      resolve: {
        params: () => extendedParams,
      },
    };

    const result = this.$uibModal.open(modalArgs).result;

    result.catch(() => {});

    return result;
  }
}

export const CANCEL_MODAL_SERVICE = 'spinnaker.core.cancelModal.service';
module(CANCEL_MODAL_SERVICE, [require('angular-ui-bootstrap'), CANCEL_MODAL_CONTROLLER]).service(
  'cancelModalService',
  CancelModalService,
);
