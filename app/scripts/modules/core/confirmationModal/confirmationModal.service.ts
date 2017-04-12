import {module} from 'angular';
import {IModalService, IModalSettings} from 'angular-ui-bootstrap';

export interface IConfirmationModalParams {
  account?: string;
  askForReason?: boolean;
  body?: string;
  buttonText?: string;
  cancelButtonText?: string;
  header?: string;
  interestingHealthProviderNames?: string[];
  multiTaskTitle?: string;
  platformHealthOnlyShowOverride?: boolean;
  platformHealthType?: string;
  reason?: string;
  size?: string;
  submitJustWithReason?: boolean;
  submitMethod?: (args: any) => ng.IPromise<any>;
  taskMonitorConfig?: any;
  taskMonitors?: any[];
  textToVerify?: string;
  verificationLabel?: string;
}

export class ConfirmationModalService {

  private defaults: IConfirmationModalParams = {
    buttonText: 'Confirm',
    cancelButtonText: 'Cancel',
  };

  public constructor(private $uibModal: IModalService, private $sce: ng.ISCEService) {}

  public confirm(params: IConfirmationModalParams): ng.IPromise<any> {
    const extendedParams: IConfirmationModalParams = Object.assign({}, this.defaults, params);

    if (extendedParams.body) {
      extendedParams.body = this.$sce.trustAsHtml(extendedParams.body);
    }

    const modalArgs: IModalSettings = {
      templateUrl: require('./confirm.html'),
      controller: 'ConfirmationModalCtrl as ctrl',
      resolve: {
        params: () => extendedParams
      }
    };

    if (params.size) {
      modalArgs.size = params.size;
    }
    return this.$uibModal.open(modalArgs).result;
  }

}

export const CONFIRMATION_MODAL_SERVICE = 'spinnaker.core.confirmationModal.service';
module(CONFIRMATION_MODAL_SERVICE, [
  require('angular-ui-bootstrap'),
  require('./confirmationModal.controller.js'),
]).service('confirmationModalService', ConfirmationModalService);

export let confirmationModalService: ConfirmationModalService = undefined;
export const ConfirmationModalServiceInject = ($injector: any) => {
    confirmationModalService = <ConfirmationModalService>$injector.get('confirmationModalService');
};
