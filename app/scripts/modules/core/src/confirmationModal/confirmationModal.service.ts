import { IPromise } from 'angular';
import { $q } from 'ngimport';

import { ConfirmModal, IConfirmModalProps } from './ConfirmModal';
import { ReactModal, toMarkdown } from 'core/presentation';
import { ITaskMonitorConfig, TaskMonitor } from 'core/task';

export interface IConfirmationModalPassthroughProps {
  account?: string;
  askForReason?: boolean;
  bodyContent?: JSX.Element;
  buttonText?: string;
  cancelButtonText?: string;
  header?: string;
  interestingHealthProviderNames?: string[];
  multiTaskTitle?: string;
  platformHealthOnlyShowOverride?: boolean;
  platformHealthType?: string;
  retryBody?: string;
  submitJustWithReason?: boolean;
  submitMethod?: (args?: any) => IPromise<any>;
  textToVerify?: string;
  verificationLabel?: string;
}

export interface IConfirmationModalParams extends IConfirmationModalPassthroughProps {
  body?: string;
  taskMonitorConfig?: ITaskMonitorConfig;
  taskMonitorConfigs?: ITaskMonitorConfig[];
}

export class ConfirmationModalService {
  private static defaults: IConfirmationModalParams = {
    buttonText: 'Confirm',
    cancelButtonText: 'Cancel',
  };

  public static confirm(params: IConfirmationModalParams): IPromise<any> {
    const extendedParams: IConfirmModalProps = { ...this.defaults, ...params };

    if (params.body) {
      extendedParams.bodyContent = toMarkdown(params.body);
    }

    const deferred = $q.defer();

    const { taskMonitorConfig, taskMonitorConfigs } = params;
    if (taskMonitorConfig) {
      extendedParams.taskMonitor = new TaskMonitor(taskMonitorConfig);
    }
    if (taskMonitorConfigs) {
      extendedParams.taskMonitors = taskMonitorConfigs.map(m => new TaskMonitor(m));
    }

    ReactModal.show(ConfirmModal, extendedParams).then(deferred.resolve, deferred.reject);

    // modal was dismissed
    deferred.promise.catch(() => {});

    return deferred.promise;
  }
}
