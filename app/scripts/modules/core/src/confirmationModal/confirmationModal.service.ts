import { module } from 'angular';
import { IModalService, IModalSettings } from 'angular-ui-bootstrap';

export interface IConfirmationModalParams {
  account?: string;
  applicationName?: string;
  askForReason?: boolean;
  body?: string;
  buttonText?: string;
  cancelButtonText?: string;
  header?: string;
  interestingHealthProviderNames?: string[];
  multiTaskTitle?: string;
  platformHealthOnlyShowOverride?: boolean;
  platformHealthType?: string;
  provider?: string;
  reason?: string;
  size?: string;
  submitJustWithReason?: boolean;
  submitMethod?: (args: any) => ng.IPromise<any>;
  taskMonitorConfig?: any;
  taskMonitors?: any[];
  textToVerify?: string;
  verificationLabel?: string;
  windowClass?: string;
}

export class ConfirmationModalService {
  private defaults: IConfirmationModalParams = {
    buttonText: 'Confirm',
    cancelButtonText: 'Cancel',
  };

  public constructor(private $uibModal: IModalService, private $sce: ng.ISCEService) {}

  public confirm(params: IConfirmationModalParams): ng.IPromise<any> {
    const extendedParams: IConfirmationModalParams = { ...this.defaults, ...params };

    if (extendedParams.body) {
      extendedParams.body = this.$sce.trustAsHtml(extendedParams.body);
    }

    const modalArgs: IModalSettings = {
      templateUrl: require('./confirm.html'),
      controller: 'ConfirmationModalCtrl as ctrl',
      resolve: {
        params: () => extendedParams,
      },
    };

    if (params.size) {
      modalArgs.size = params.size;
    }

    if (params.windowClass) {
      modalArgs.windowClass = params.windowClass;
    }

    const result = this.$uibModal.open(modalArgs).result;

    result.catch(() => {});

    return result;
  }
}

export const CONFIRMATION_MODAL_SERVICE = 'spinnaker.core.confirmationModal.service';
module(CONFIRMATION_MODAL_SERVICE, [
  require('angular-ui-bootstrap'),
  require('./confirmationModal.controller').name,
]).service('confirmationModalService', ConfirmationModalService);
