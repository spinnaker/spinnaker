import type { IConfirmModalProps } from './ConfirmModal';
import { ConfirmModal } from './ConfirmModal';
import { toMarkdown } from '../presentation/Markdown';
import { ReactModal } from '../presentation/ReactModal';
import type { ITaskMonitorConfig } from '../task';
import { TaskMonitor } from '../task';
import { diagnosticLogger } from '../utils/diagnosticLogger';

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
  reasonPlaceholder?: string;
  reasonRequired?: boolean;
  retryBody?: string;
  submitJustWithReason?: boolean;
  submitMethod?: (args?: any) => PromiseLike<any>;
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

  public static confirm(params: IConfirmationModalParams): PromiseLike<any> {
    const extendedParams: IConfirmModalProps = { ...this.defaults, ...params };

    if (params.body) {
      extendedParams.bodyContent = toMarkdown(params.body);
    }

    const { taskMonitorConfig, taskMonitorConfigs } = params;
    if (taskMonitorConfig) {
      extendedParams.taskMonitor = new TaskMonitor(taskMonitorConfig);
    }
    if (taskMonitorConfigs) {
      extendedParams.taskMonitors = taskMonitorConfigs.map((m) => new TaskMonitor(m));
    }

    const modalPromise = ReactModal.show(ConfirmModal, extendedParams);
    const monitors = [extendedParams.taskMonitor, ...(extendedParams.taskMonitors || [])].filter(Boolean);
    let cleanedUp = false;
    const cleanupTaskMonitors = () => {
      if (cleanedUp) {
        return;
      }
      cleanedUp = true;
      monitors.forEach((monitor) => {
        try {
          monitor.onModalClose();
        } catch (error) {
          diagnosticLogger.error('Failed to clean up confirmation task monitor', error);
        }
      });
    };
    modalPromise.then(cleanupTaskMonitors, cleanupTaskMonitors);

    return modalPromise;
  }
}
