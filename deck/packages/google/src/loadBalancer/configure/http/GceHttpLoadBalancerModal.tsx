import React from 'react';

import type { Application, IModalComponentProps, ITask, ITaskCommand } from '@spinnaker/core';
import { ModalClose, ReactModal, TaskMonitor, TaskMonitorWrapper } from '@spinnaker/core';

import {
  constrainGceHttpLoadBalancerCommand,
  GceHttpLoadBalancerEditor,
  validateGceHttpLoadBalancerCommand,
} from './GceHttpLoadBalancerEditor';
import type {
  GceLoadBalancerEditorMode,
  GceLoadBalancerType,
  IGceLoadBalancerCommand,
  IGceLoadBalancerData,
  IGceLoadBalancerDataReaders,
  IGceLoadBalancerDataState,
} from '../common';
import {
  GceLoadBalancerDataController,
  normalizeGceLoadBalancerCommand,
  submitGceLoadBalancerCommand,
} from '../common';

const EMPTY_DATA: IGceLoadBalancerData = {
  accounts: [],
  addresses: [],
  backendServices: [],
  certificates: [],
  healthChecks: [],
  networks: [],
  regions: [],
  subnets: [],
};

export interface IGceHttpLoadBalancerModalProps extends IModalComponentProps {
  app?: Application;
  application?: Application;
  credentials?: string;
  data?: IGceLoadBalancerData;
  dataReaders?: IGceLoadBalancerDataReaders;
  executeTask?: (taskCommand: ITaskCommand) => PromiseLike<ITask>;
  forPipelineConfig?: boolean;
  isNew?: boolean;
  loadBalancer?: Record<string, unknown>;
  loadBalancerType?: Extract<GceLoadBalancerType, 'HTTP' | 'INTERNAL_MANAGED'>;
  mode?: GceLoadBalancerEditorMode;
}

interface IGceHttpLoadBalancerModalState extends IGceLoadBalancerDataState {
  command: IGceLoadBalancerCommand;
  taskMonitor: TaskMonitor;
}

export function initializeGceHttpLoadBalancerCommand(
  source: Record<string, unknown>,
  mode: GceLoadBalancerEditorMode,
  application: Application,
  defaults: Partial<Record<'credentials' | 'loadBalancerType', unknown>> = {},
): IGceLoadBalancerCommand {
  const input = {
    ...defaults,
    ...source,
    credentials: source.credentials || source.account || defaults.credentials,
    loadBalancerType: source.loadBalancerType || defaults.loadBalancerType || 'HTTP',
    name: source.name || source.loadBalancerName || source.urlMapName || application?.name || '',
  };
  const command = normalizeGceLoadBalancerCommand(input, mode);
  const hasPersistedListener =
    (Array.isArray(source.listeners) && source.listeners.length > 0) ||
    source.port !== undefined ||
    source.portRange !== undefined ||
    source.ports !== undefined;
  if (!hasPersistedListener) {
    command.listeners = [{ name: command.name, portRange: '80', protocol: 'HTTP' }];
  }
  return constrainGceHttpLoadBalancerCommand(command);
}

export class GceHttpLoadBalancerModal extends React.Component<
  IGceHttpLoadBalancerModalProps,
  IGceHttpLoadBalancerModalState
> {
  public static supportsPipelineConfig = true;

  public static show(props: IGceHttpLoadBalancerModalProps): Promise<unknown> {
    return ReactModal.show(GceHttpLoadBalancerModal, props, { dialogClassName: 'wizard-modal modal-lg' });
  }

  private dataController?: GceLoadBalancerDataController;
  private unsubscribe?: () => void;

  public constructor(props: IGceHttpLoadBalancerModalProps) {
    super(props);
    const application = this.application(props);
    const mode = this.mode(props);
    const command = initializeGceHttpLoadBalancerCommand(props.loadBalancer || {}, mode, application, {
      credentials: props.credentials,
      loadBalancerType: props.loadBalancerType,
    });
    this.state = {
      command,
      data: props.data || EMPTY_DATA,
      status: props.data ? 'ready' : 'idle',
      taskMonitor: new TaskMonitor({
        application,
        title: `${mode === 'edit' ? 'Updating' : 'Creating'} your load balancer`,
        modalInstance: TaskMonitor.modalInstanceEmulation(
          () => props.closeModal(),
          () => props.dismissModal(),
        ),
        onTaskComplete: () => {
          application.loadBalancers?.refresh?.();
          props.closeModal();
        },
      }),
    };
  }

  public componentDidMount(): void {
    if (this.props.data) {
      return;
    }
    this.dataController = new GceLoadBalancerDataController(this.props.dataReaders);
    this.unsubscribe = this.dataController.subscribe((state) => this.setState(state));
    this.dataController.load(this.state.command.credentials);
  }

  public componentWillUnmount(): void {
    this.unsubscribe?.();
    this.dataController?.dispose();
  }

  private application(props = this.props): Application {
    return (props.application || props.app) as Application;
  }

  private mode(props = this.props): GceLoadBalancerEditorMode {
    if (props.forPipelineConfig) {
      return 'pipeline';
    }
    if (props.mode) {
      return props.mode;
    }
    return props.isNew === false || props.loadBalancer ? 'edit' : 'create';
  }

  private updateCommand = (command: IGceLoadBalancerCommand): void => {
    const accountChanged = command.credentials !== this.state.command.credentials;
    this.setState({ command });
    if (accountChanged && this.dataController) {
      this.dataController.load(command.credentials);
    }
  };

  private submit = (): void => {
    if (validateGceHttpLoadBalancerCommand(this.state.command).length) {
      return;
    }
    const command = constrainGceHttpLoadBalancerCommand(this.state.command);
    const dependencies = {
      application: this.application(),
      executeTask: this.props.executeTask,
    };
    if (command.mode === 'pipeline') {
      this.props.closeModal(submitGceLoadBalancerCommand(command, dependencies));
      return;
    }
    this.state.taskMonitor.submit(() => submitGceLoadBalancerCommand(command, dependencies) as PromiseLike<ITask>);
  };

  public render(): JSX.Element {
    const { command, data, status, taskMonitor } = this.state;
    const validationErrors = validateGceHttpLoadBalancerCommand(command);
    const heading =
      command.mode === 'create'
        ? `Create ${command.loadBalancerType === 'HTTP' ? 'HTTP(S)' : 'Internal managed HTTP(S)'} load balancer`
        : `Edit ${command.name}`;

    return (
      <form className="form-horizontal gce-http-lb" name="form">
        <div className="modal-header">
          <ModalClose dismiss={this.props.dismissModal} />
          <h3>{heading}</h3>
        </div>
        <div className="modal-body">
          <TaskMonitorWrapper monitor={taskMonitor} />
          {status === 'loading' && <div className="horizontal center middle">Loading...</div>}
          {status === 'error' && <div className="alert alert-danger">Unable to load GCE resources.</div>}
          <GceHttpLoadBalancerEditor command={command} data={data} onChange={this.updateCommand} />
          {!!validationErrors.length && (
            <div className="alert alert-danger gce-http-validation-errors">
              {validationErrors.map((error) => (
                <div key={error}>{error}</div>
              ))}
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button
            type="button"
            className="btn btn-default"
            disabled={taskMonitor.submitting}
            onClick={this.props.dismissModal}
          >
            Cancel
          </button>
          <button
            type="button"
            className="btn btn-primary"
            disabled={taskMonitor.submitting || status === 'loading' || !!validationErrors.length}
            onClick={this.submit}
          >
            {command.mode === 'pipeline' ? 'Done' : command.mode === 'edit' ? 'Update' : 'Create'}
          </button>
        </div>
      </form>
    );
  }
}
