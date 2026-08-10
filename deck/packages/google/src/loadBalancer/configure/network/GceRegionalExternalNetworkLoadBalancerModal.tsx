import React from 'react';

import type { Application, IModalComponentProps, ITask, ITaskCommand } from '@spinnaker/core';
import {
  InfrastructureCaches,
  ModalClose,
  ReactModal,
  SubmitButton,
  TaskExecutor,
  TaskMonitor,
  TaskMonitorWrapper,
} from '@spinnaker/core';

import type { IGceRegionalExternalNetworkLoadBalancerCommand } from './GceRegionalExternalNetworkLoadBalancerEditor';
import {
  createGceRegionalExternalNetworkHealthCheck,
  GceRegionalExternalNetworkLoadBalancerEditor,
  validateGceRegionalExternalNetworkLoadBalancerCommand,
} from './GceRegionalExternalNetworkLoadBalancerEditor';
import type {
  GceLoadBalancerEditorMode,
  IGceLoadBalancerData,
  IGceLoadBalancerDataReaders,
  IGceLoadBalancerDataState,
  IGceLoadBalancerHealthCheck,
  IGceResourceReference,
  IGceSerializedLoadBalancerCommand,
} from '../common';
import {
  GceLoadBalancerDataController,
  normalizeGceLoadBalancerCommand,
  serializeGceLoadBalancerCommand,
} from '../common';
import { GCEProviderSettings } from '../../../gce.settings';

type UnknownRecord = Record<string, any>;

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

export interface IGceRegionalExternalNetworkLoadBalancerPayload extends IGceSerializedLoadBalancerCommand {
  backendService: UnknownRecord;
  ipAddress?: string;
  ipProtocol: string;
  networkTier?: string;
  ports: string[];
}

export interface IGceRegionalExternalNetworkLoadBalancerModalProps extends IModalComponentProps {
  app?: Application;
  application?: Application;
  credentials?: string;
  data?: IGceLoadBalancerData;
  dataReaders?: IGceLoadBalancerDataReaders;
  executeTask?: (taskCommand: ITaskCommand) => PromiseLike<ITask>;
  forPipelineConfig?: boolean;
  isNew?: boolean;
  loadBalancer?: UnknownRecord;
  mode?: GceLoadBalancerEditorMode;
}

interface IGceRegionalExternalNetworkLoadBalancerModalState extends IGceLoadBalancerDataState {
  command: IGceRegionalExternalNetworkLoadBalancerCommand;
  taskMonitor: TaskMonitor;
}

interface IGceRegionalExternalNetworkSubmissionDependencies {
  application: Application;
  executeTask?: (taskCommand: ITaskCommand) => PromiseLike<ITask>;
}

export function normalizeGceRegionalExternalNetworkLoadBalancerCommand(
  persisted: UnknownRecord | null | undefined = {},
  mode: GceLoadBalancerEditorMode,
  applicationName = '',
  defaults: Partial<Record<'credentials' | 'region', unknown>> = {},
): IGceRegionalExternalNetworkLoadBalancerCommand {
  const source = persisted || {};
  const backendService = source.backendService || source.backendServices?.[0];
  const normalizedSource: UnknownRecord = {
    ...defaults,
    ...source,
    backendServices: backendService ? [{ ...backendService }] : source.backendServices,
    credentials: source.credentials || source.account || defaults.credentials,
    loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
    name: source.name || source.loadBalancerName || applicationName,
  };

  const command = normalizeGceLoadBalancerCommand(
    normalizedSource,
    mode,
  ) as IGceRegionalExternalNetworkLoadBalancerCommand;
  const listener = command.listeners[0];
  const ports = splitPorts(listener?.portRange);
  let backendServices = command.backendServices;
  let healthChecks = command.healthChecks;

  if (mode === 'create') {
    if (backendService && !backendServices.length) {
      backendServices = [
        {
          name: String(backendService.name || ''),
          sessionAffinity: backendService.sessionAffinity || 'NONE',
          ...(backendService.healthCheck ? { healthCheck: backendService.healthCheck } : {}),
        },
      ];
    } else if (!backendServices.length) {
      const defaultHealthCheck = createGceRegionalExternalNetworkHealthCheck(command.name);
      backendServices = [
        {
          healthCheck: (defaultHealthCheck as unknown) as IGceResourceReference,
          name: command.name,
          sessionAffinity: 'NONE',
        },
      ];
      healthChecks = [defaultHealthCheck];
    }
  }

  const backend = backendServices[0];
  const backendHealthCheck =
    typeof backend?.healthCheck === 'object' ? (backend.healthCheck as IGceLoadBalancerHealthCheck) : undefined;
  const referencedHealthCheck = backendHealthCheck?.name
    ? healthChecks.find(({ name }) => name === backendHealthCheck.name)
    : undefined;
  const healthCheck =
    (backendHealthCheck && Object.keys(backendHealthCheck).some((key) => key !== 'name' && key !== 'selfLink')
      ? backendHealthCheck
      : referencedHealthCheck || backendHealthCheck || healthChecks[0]) || undefined;
  if (backend && healthCheck) {
    const namedHealthCheck = {
      ...healthCheck,
      name: healthCheck.name?.trim() || command.name,
    };
    backendServices = [
      {
        ...backend,
        healthCheck: (namedHealthCheck as unknown) as IGceResourceReference,
      },
    ];
    healthChecks = [namedHealthCheck];
  }

  return {
    ...command,
    backendServices,
    healthChecks,
    loadBalancerType: 'REGIONAL_EXTERNAL_NETWORK',
    ports,
  };
}

export function serializeGceRegionalExternalNetworkLoadBalancerCommand(
  command: IGceRegionalExternalNetworkLoadBalancerCommand,
): IGceRegionalExternalNetworkLoadBalancerPayload {
  const syncedCommand: IGceRegionalExternalNetworkLoadBalancerCommand = {
    ...command,
    listeners: [
      {
        ...(command.listeners[0] || { name: command.name, portRange: '', protocol: 'TCP' }),
        portRange: (command.ports?.length ? command.ports : splitPorts(command.listeners[0]?.portRange)).join(','),
      },
    ],
  };

  return serializeGceLoadBalancerCommand(syncedCommand) as IGceRegionalExternalNetworkLoadBalancerPayload;
}

export function submitGceRegionalExternalNetworkLoadBalancerCommand(
  command: IGceRegionalExternalNetworkLoadBalancerCommand,
  { application, executeTask = TaskExecutor.executeTask }: IGceRegionalExternalNetworkSubmissionDependencies,
): IGceRegionalExternalNetworkLoadBalancerPayload | Promise<ITask> {
  const payload = serializeGceRegionalExternalNetworkLoadBalancerCommand(command);
  if (command.mode === 'pipeline') {
    return payload;
  }

  return Promise.resolve(
    executeTask({
      application,
      description: `${command.mode === 'edit' ? 'Update' : 'Create'} Load Balancer: ${command.name}`,
      job: [payload],
    }),
  );
}

class GceRegionalExternalNetworkLoadBalancerModalComponent extends React.Component<
  IGceRegionalExternalNetworkLoadBalancerModalProps,
  IGceRegionalExternalNetworkLoadBalancerModalState
> {
  private dataController?: GceLoadBalancerDataController;
  private unsubscribe?: () => void;

  public constructor(props: IGceRegionalExternalNetworkLoadBalancerModalProps) {
    super(props);
    const application = this.application(props);
    const mode = this.mode(props);
    const command = normalizeGceRegionalExternalNetworkLoadBalancerCommand(
      props.loadBalancer,
      mode,
      application?.name,
      {
        credentials: props.credentials || GCEProviderSettings.defaults.account,
        region: GCEProviderSettings.defaults.region,
      },
    );
    this.state = {
      command,
      data: props.data || EMPTY_DATA,
      status: props.data ? 'ready' : 'idle',
      taskMonitor: new TaskMonitor({
        application,
        title: `${mode === 'edit' ? 'Updating' : 'Creating'} your load balancer`,
        onDismiss: () => props.dismissModal?.(),
        onTaskComplete: () => {
          InfrastructureCaches.clearCache('healthChecks');
          application?.loadBalancers?.refresh?.();
          props.closeModal?.();
        },
      }),
    };
  }

  public componentDidMount(): void {
    if (this.props.data) return;
    this.dataController = new GceLoadBalancerDataController(this.props.dataReaders);
    this.unsubscribe = this.dataController.subscribe((state) => this.setState(state));
    this.dataController.load(this.state.command.credentials);
  }

  public componentWillUnmount(): void {
    this.unsubscribe?.();
    this.dataController?.dispose();
  }

  public render(): JSX.Element {
    const { command, data, status, taskMonitor } = this.state;
    const errors = validateGceRegionalExternalNetworkLoadBalancerCommand(command);
    const heading =
      command.mode === 'create'
        ? 'Create Regional External Network Load Balancer'
        : command.mode === 'pipeline'
        ? 'Configure Regional External Network Load Balancer'
        : `Edit ${command.name}`;

    return (
      <div className="modal-content">
        <TaskMonitorWrapper monitor={taskMonitor} />
        <ModalClose dismiss={this.props.dismissModal} />
        <div className="modal-header">
          <h3>{heading}</h3>
        </div>
        <div className="modal-body">
          {status === 'loading' && <div className="horizontal center middle">Loading...</div>}
          {status === 'error' && <div className="alert alert-danger">Unable to load GCE resources.</div>}
          <GceRegionalExternalNetworkLoadBalancerEditor command={command} data={data} onChange={this.updateCommand} />
          {!!errors.length && (
            <div className="alert alert-danger gce-regional-external-network-validation-errors">
              {errors.map((error) => (
                <div key={error}>{error}</div>
              ))}
            </div>
          )}
        </div>
        <div className="modal-footer">
          <button
            className="btn btn-default"
            disabled={taskMonitor.submitting}
            onClick={this.props.dismissModal}
            type="button"
          >
            Cancel
          </button>
          <SubmitButton
            isDisabled={!!errors.length || status === 'loading' || taskMonitor.submitting}
            isFormSubmit={false}
            label={command.mode === 'pipeline' ? 'Done' : command.mode === 'edit' ? 'Update' : 'Create'}
            onClick={this.submit}
            submitting={taskMonitor.submitting}
          />
        </div>
      </div>
    );
  }

  private application(props = this.props): Application {
    return (props.application || props.app) as Application;
  }

  private mode(props = this.props): GceLoadBalancerEditorMode {
    if (props.forPipelineConfig) return 'pipeline';
    if (props.mode) return props.mode;
    return props.isNew === false || props.loadBalancer ? 'edit' : 'create';
  }

  private updateCommand = (command: IGceRegionalExternalNetworkLoadBalancerCommand): void => {
    const accountChanged = command.credentials !== this.state.command.credentials;
    this.setState({ command });
    if (accountChanged) this.dataController?.load(command.credentials);
  };

  private submit = (): void => {
    if (this.state.command.mode === 'pipeline') {
      this.props.closeModal?.(
        submitGceRegionalExternalNetworkLoadBalancerCommand(this.state.command, {
          application: this.application(),
          executeTask: this.props.executeTask,
        }),
      );
      return;
    }
    this.state.taskMonitor.submit(
      () =>
        submitGceRegionalExternalNetworkLoadBalancerCommand(this.state.command, {
          application: this.application(),
          executeTask: this.props.executeTask,
        }) as PromiseLike<ITask>,
    );
  };
}

export const GceRegionalExternalNetworkLoadBalancerModal = Object.assign(
  GceRegionalExternalNetworkLoadBalancerModalComponent,
  {
    show: (props: IGceRegionalExternalNetworkLoadBalancerModalProps) =>
      ReactModal.show(GceRegionalExternalNetworkLoadBalancerModalComponent, props, { dialogClassName: 'modal-lg' }),
    supportsPipelineConfig: true,
  },
);

function splitPorts(value: string | undefined): string[] {
  return String(value || '')
    .split(',')
    .map((port) => port.trim())
    .filter(Boolean);
}
