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

import type { GceNetworkSessionAffinity, IGceNetworkLoadBalancerCommand } from './GceNetworkLoadBalancerEditor';
import {
  createGceNetworkHealthCheck,
  GceNetworkLoadBalancerEditor,
  validateGceNetworkLoadBalancerCommand,
} from './GceNetworkLoadBalancerEditor';
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
const SESSION_AFFINITIES: GceNetworkSessionAffinity[] = ['NONE', 'CLIENT_IP', 'CLIENT_IP_PROTO'];

interface IGceNetworkHealthCheckPayload {
  checkIntervalSec?: number;
  healthyThreshold?: number;
  port?: number;
  requestPath?: string;
  timeoutSec?: number;
  unhealthyThreshold?: number;
}

export interface IGceNetworkLoadBalancerPayload extends IGceSerializedLoadBalancerCommand {
  healthCheck: IGceNetworkHealthCheckPayload | null;
  sessionAffinity: GceNetworkSessionAffinity;
}

export interface IGceNetworkLoadBalancerModalProps extends IModalComponentProps {
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

interface IGceNetworkLoadBalancerModalState extends IGceLoadBalancerDataState {
  command: IGceNetworkLoadBalancerCommand;
  taskMonitor: TaskMonitor;
}

interface IGceNetworkSubmissionDependencies {
  application: Application;
  executeTask?: (taskCommand: ITaskCommand) => PromiseLike<ITask>;
}

export function normalizeGceNetworkLoadBalancerCommand(
  persisted: UnknownRecord | null | undefined = {},
  mode: GceLoadBalancerEditorMode,
  applicationName = '',
  defaults: Partial<Record<'credentials' | 'region', unknown>> = {},
): IGceNetworkLoadBalancerCommand {
  const persistedLoadBalancer = persisted || {};
  const details =
    persistedLoadBalancer.elb && typeof persistedLoadBalancer.elb === 'object' ? persistedLoadBalancer.elb : {};
  const listener = details.listenerDescriptions?.[0]?.listener || {};
  const sourceDetails: UnknownRecord = {
    ...details,
    ...persistedLoadBalancer,
    healthCheck:
      persistedLoadBalancer.healthCheck !== undefined ? persistedLoadBalancer.healthCheck : details.healthCheck,
    ipAddress: persistedLoadBalancer.ipAddress !== undefined ? persistedLoadBalancer.ipAddress : details.ipAddress,
    ipProtocol:
      persistedLoadBalancer.ipProtocol !== undefined
        ? persistedLoadBalancer.ipProtocol
        : details.ipProtocol || listener.protocol,
    portRange:
      persistedLoadBalancer.portRange !== undefined
        ? persistedLoadBalancer.portRange
        : details.portRange || listener.loadBalancerPort,
    sessionAffinity:
      persistedLoadBalancer.sessionAffinity !== undefined
        ? persistedLoadBalancer.sessionAffinity
        : details.sessionAffinity,
    targetPool: persistedLoadBalancer.targetPool !== undefined ? persistedLoadBalancer.targetPool : details.targetPool,
  };
  const hasHealthCheck =
    Object.prototype.hasOwnProperty.call(persistedLoadBalancer, 'healthCheck') ||
    Object.prototype.hasOwnProperty.call(details, 'healthCheck') ||
    sourceDetails.healthChecks;
  const persistedHealthChecks = sourceDetails.healthChecks?.length
    ? sourceDetails.healthChecks
    : sourceDetails.healthCheck
    ? [normalizeHealthCheckInput(sourceDetails.healthCheck)]
    : [];
  const source: UnknownRecord = {
    ...defaults,
    ...sourceDetails,
    credentials: sourceDetails.credentials || sourceDetails.account || defaults.credentials,
    healthChecks: persistedHealthChecks,
    ipAddress: normalizeReference(sourceDetails.ipAddress),
    loadBalancerType: 'NETWORK',
    name: sourceDetails.name || sourceDetails.loadBalancerName || applicationName,
    network: normalizeReference(sourceDetails.network),
    region: sourceDetails.region || defaults.region,
  };
  const command = normalizeGceLoadBalancerCommand(source, mode);
  const hasListener =
    (Array.isArray(sourceDetails.listeners) && sourceDetails.listeners.length > 0) ||
    sourceDetails.ipAddress !== undefined ||
    sourceDetails.ipProtocol !== undefined ||
    sourceDetails.portRange !== undefined ||
    sourceDetails.ports !== undefined;
  if (!hasListener) {
    command.listeners = [{ name: command.name, portRange: '8080', protocol: 'TCP' }];
  }
  if (mode === 'create' && !hasHealthCheck) {
    command.healthChecks = [createGceNetworkHealthCheck()];
  }

  return {
    ...command,
    loadBalancerType: 'NETWORK',
    sessionAffinity: normalizeSessionAffinity(sourceDetails.sessionAffinity),
    targetPool: normalizeReference(sourceDetails.targetPool),
  } as IGceNetworkLoadBalancerCommand;
}

export function serializeGceNetworkLoadBalancerCommand(
  command: IGceNetworkLoadBalancerCommand,
): IGceNetworkLoadBalancerPayload {
  const serialized = serializeGceLoadBalancerCommand(command);
  delete serialized.healthChecks;
  delete serialized.listeners;
  delete serialized.network;
  delete serialized.targetPool;
  delete serialized.mode;

  return {
    ...serialized,
    healthCheck: serializeHealthCheck(command.healthChecks[0]),
    sessionAffinity: command.sessionAffinity,
  } as IGceNetworkLoadBalancerPayload;
}

export function submitGceNetworkLoadBalancerCommand(
  command: IGceNetworkLoadBalancerCommand,
  { application, executeTask = TaskExecutor.executeTask }: IGceNetworkSubmissionDependencies,
): IGceNetworkLoadBalancerPayload | PromiseLike<ITask> {
  const payload = serializeGceNetworkLoadBalancerCommand(command);
  if (command.mode === 'pipeline') {
    return payload;
  }

  return executeTask({
    application,
    description: `${command.mode === 'edit' ? 'Update' : 'Create'} Load Balancer: ${command.name}`,
    job: [payload],
  });
}

class GceNetworkLoadBalancerModalComponent extends React.Component<
  IGceNetworkLoadBalancerModalProps,
  IGceNetworkLoadBalancerModalState
> {
  private dataController?: GceLoadBalancerDataController;
  private unsubscribe?: () => void;

  public constructor(props: IGceNetworkLoadBalancerModalProps) {
    super(props);
    const application = this.application(props);
    const mode = this.mode(props);
    const command = normalizeGceNetworkLoadBalancerCommand(props.loadBalancer, mode, application?.name, {
      credentials: props.credentials || GCEProviderSettings.defaults.account,
      region: GCEProviderSettings.defaults.region,
    });
    this.state = {
      command,
      data: props.data || EMPTY_DATA,
      status: props.data ? 'ready' : 'idle',
      taskMonitor: new TaskMonitor({
        application,
        title: `${mode === 'edit' ? 'Updating' : 'Creating'} your load balancer`,
        modalInstance: TaskMonitor.modalInstanceEmulation(
          () => props.closeModal?.(),
          () => props.dismissModal?.(),
        ),
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
    const errors = validateGceNetworkLoadBalancerCommand(command);
    const heading =
      command.mode === 'create'
        ? 'Create Network Load Balancer'
        : command.mode === 'pipeline'
        ? 'Configure Network Load Balancer'
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
          <GceNetworkLoadBalancerEditor command={command} data={data} onChange={this.updateCommand} />
          {!!errors.length && (
            <div className="alert alert-danger gce-network-validation-errors">
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

  private updateCommand = (command: IGceNetworkLoadBalancerCommand): void => {
    const accountChanged = command.credentials !== this.state.command.credentials;
    this.setState({ command });
    if (accountChanged) this.dataController?.load(command.credentials);
  };

  private submit = (): void => {
    if (this.state.command.mode === 'pipeline') {
      this.props.closeModal?.(
        submitGceNetworkLoadBalancerCommand(this.state.command, {
          application: this.application(),
          executeTask: this.props.executeTask,
        }),
      );
      return;
    }
    this.state.taskMonitor.submit(
      () =>
        submitGceNetworkLoadBalancerCommand(this.state.command, {
          application: this.application(),
          executeTask: this.props.executeTask,
        }) as PromiseLike<ITask>,
    );
  };
}

export const GceNetworkLoadBalancerModal = Object.assign(GceNetworkLoadBalancerModalComponent, {
  show: (props: IGceNetworkLoadBalancerModalProps) =>
    ReactModal.show(GceNetworkLoadBalancerModalComponent, props, { dialogClassName: 'modal-lg' }),
  supportsPipelineConfig: true,
});

function normalizeHealthCheckInput(value: unknown): UnknownRecord {
  if (value && typeof value === 'object') return value as UnknownRecord;
  const reference = normalizeReference(value);
  return reference || {};
}

function normalizeReference(value: unknown): IGceResourceReference | undefined {
  if (!value) return undefined;
  if (typeof value === 'object') {
    const reference = value as UnknownRecord;
    const selfLink = String(reference.selfLink || '');
    const name = String(reference.name || reference.id || selfLink.split('/').filter(Boolean).pop() || '');
    return name ? ({ ...reference, name } as IGceResourceReference) : undefined;
  }
  const selfLink = String(value);
  const name = selfLink.split('/').filter(Boolean).pop() || selfLink;
  return selfLink.includes('/') ? { name, selfLink } : { name };
}

function normalizeSessionAffinity(value: unknown): GceNetworkSessionAffinity {
  const normalized = String(value || 'NONE').toUpperCase() as GceNetworkSessionAffinity;
  return SESSION_AFFINITIES.includes(normalized) ? normalized : 'NONE';
}

function serializeHealthCheck(healthCheck?: IGceLoadBalancerHealthCheck): IGceNetworkHealthCheckPayload | null {
  if (!healthCheck) return null;
  return {
    checkIntervalSec: numberOrUndefined(healthCheck.checkIntervalSec),
    healthyThreshold: numberOrUndefined(healthCheck.healthyThreshold),
    port: numberOrUndefined(healthCheck.port),
    requestPath: healthCheck.requestPath,
    timeoutSec: numberOrUndefined(healthCheck.timeoutSec),
    unhealthyThreshold: numberOrUndefined(healthCheck.unhealthyThreshold),
  };
}

function numberOrUndefined(value: unknown): number | undefined {
  return value === undefined || value === null || value === '' ? undefined : Number(value);
}
