import React from 'react';

import type { Application, IModalComponentProps } from '@spinnaker/core';
import {
  InfrastructureCaches,
  LoadBalancerWriter,
  ModalClose,
  ReactModal,
  Spinner,
  SubmitButton,
  TaskMonitor,
  TaskMonitorWrapper,
} from '@spinnaker/core';

import {
  applyGceProxyTypeConstraints,
  GceProxyLoadBalancerEditor,
  validateGceProxyLoadBalancerCommand,
} from './GceProxyLoadBalancerEditor';
import type { GceProxyLoadBalancerType } from './GceProxyLoadBalancerEditor';
import type { IGceLoadBalancerDataReaders } from './gceLoadBalancerData';
import type {
  GceLoadBalancerEditorMode,
  IGceLoadBalancerCommand,
  IGceSerializedLoadBalancerCommand,
} from './gceLoadBalancerModels';
import { normalizeGceLoadBalancerCommand, serializeGceLoadBalancerCommand } from './gceLoadBalancerModels';
import { useGceLoadBalancerData } from './useGceLoadBalancerData';

type UnknownRecord = Record<string, any>;

export interface IGceProxyLoadBalancerPayload extends IGceSerializedLoadBalancerCommand {
  backendService: UnknownRecord;
  backendServices?: undefined;
  healthChecks?: undefined;
  ports?: string[];
}

export interface IGceProxyLoadBalancerModalProps extends IModalComponentProps {
  app?: Application;
  application?: Application;
  forPipelineConfig?: boolean;
  isNew?: boolean;
  loadBalancer?: UnknownRecord;
  loadBalancerType?: GceProxyLoadBalancerType | string;
  readers?: IGceLoadBalancerDataReaders;
}

interface IGceProxySubmissionDependencies {
  application: Application;
  closeModal?: (command: IGceProxyLoadBalancerPayload) => void;
  taskMonitor: Pick<TaskMonitor, 'submit'>;
}

export function normalizeGceProxyLoadBalancerCommand(
  persisted: UnknownRecord | undefined,
  mode: GceLoadBalancerEditorMode,
  loadBalancerType: GceProxyLoadBalancerType,
  applicationName = '',
): IGceLoadBalancerCommand {
  const source = persisted || {};
  const backendService = source.backendService || source.backendServices?.[0];
  const nestedHealthCheck = backendService?.healthCheck;
  const normalizedSource: UnknownRecord = {
    ...source,
    backendServices: backendService ? [{ ...backendService }] : source.backendServices,
    healthChecks: source.healthChecks?.length ? source.healthChecks : nestedHealthCheck ? [nestedHealthCheck] : [],
    loadBalancerType,
    name: source.name || source.loadBalancerName || applicationName,
  };

  const normalized = normalizeGceLoadBalancerCommand(normalizedSource, mode);
  if (normalized.backendServices[0]?.healthCheck) {
    const { name, selfLink } = normalized.backendServices[0].healthCheck;
    normalized.backendServices[0].healthCheck = selfLink ? { name, selfLink } : { name };
  }
  return applyGceProxyTypeConstraints(normalized);
}

export function serializeGceProxyLoadBalancerCommand(command: IGceLoadBalancerCommand): IGceProxyLoadBalancerPayload {
  const constrained = applyGceProxyTypeConstraints(command);
  const serialized = serializeGceLoadBalancerCommand(constrained) as IGceProxyLoadBalancerPayload;
  const backendSource = constrained.backendServices[0] || ({ name: constrained.name } as UnknownRecord);
  const backendService: UnknownRecord = { ...backendSource };
  const healthCheck = constrained.healthChecks[0] || backendSource.healthCheck;

  delete backendService.protocol;
  backendService.protocol = undefined;
  backendService.healthCheck = healthCheck ? { ...healthCheck } : undefined;
  backendService.name = backendService.name || constrained.name;
  serialized.backendService = backendService;
  delete serialized.backendServices;
  delete serialized.healthChecks;

  if (constrained.loadBalancerType === 'INTERNAL') {
    serialized.ipProtocol = constrained.listeners[0]?.protocol || 'TCP';
    serialized.ports = splitPorts(constrained.listeners[0]?.portRange);
    delete serialized.portRange;
  } else {
    serialized.ipProtocol = String(backendSource.protocol || 'TCP');
  }

  return serialized;
}

export function submitGceProxyLoadBalancerCommand(
  command: IGceLoadBalancerCommand,
  { application, closeModal, taskMonitor }: IGceProxySubmissionDependencies,
): void {
  const payload = serializeGceProxyLoadBalancerCommand(command);
  if (command.mode === 'pipeline') {
    closeModal?.(payload);
    return;
  }

  const descriptor = command.mode === 'edit' ? 'Update' : 'Create';
  taskMonitor.submit(() =>
    LoadBalancerWriter.upsertLoadBalancer(payload as any, application, descriptor, { healthCheck: {} }),
  );
}

function GceProxyLoadBalancerModalComponent(props: IGceProxyLoadBalancerModalProps): JSX.Element {
  const application = (props.application || props.app) as Application;
  const mode: GceLoadBalancerEditorMode = props.forPipelineConfig
    ? 'pipeline'
    : props.isNew === false
    ? 'edit'
    : 'create';
  const type = resolveProxyType(props.loadBalancerType || props.loadBalancer?.loadBalancerType);
  const [command, setCommand] = React.useState(() =>
    normalizeGceProxyLoadBalancerCommand(props.loadBalancer, mode, type, application?.name),
  );
  const dataState = useGceLoadBalancerData(command.credentials, props.readers);
  const [taskMonitor] = React.useState(
    () =>
      new TaskMonitor({
        application,
        title: `${mode === 'edit' ? 'Updating' : 'Creating'} your load balancer`,
        modalInstance: TaskMonitor.modalInstanceEmulation(
          () => props.closeModal?.(),
          () => props.dismissModal?.(),
        ),
        onTaskComplete: () => {
          InfrastructureCaches.clearCache('healthChecks');
          const loadBalancers = (application as any)?.loadBalancers || application?.getDataSource?.('loadBalancers');
          const close = () => props.closeModal?.();
          if (loadBalancers?.onNextRefresh) {
            loadBalancers.onNextRefresh(null, close);
            loadBalancers.refresh?.();
          } else {
            close();
          }
        },
      }),
  );
  const errors = validateGceProxyLoadBalancerCommand(command);
  const submitting = taskMonitor.submitting;
  const heading =
    mode === 'create'
      ? `Create ${type} Load Balancer`
      : mode === 'pipeline'
      ? `Configure ${type} Load Balancer`
      : `Edit ${command.name}`;
  const submit = () =>
    submitGceProxyLoadBalancerCommand(command, { application, closeModal: props.closeModal, taskMonitor });

  return (
    <div className="modal-content">
      <TaskMonitorWrapper monitor={taskMonitor} />
      <ModalClose dismiss={props.dismissModal} />
      <div className="modal-header">
        <h3>{heading}</h3>
      </div>
      <div className="modal-body">
        {dataState.status === 'loading' && <Spinner size="large" />}
        {dataState.status === 'error' && (
          <div className="alert alert-danger">Unable to load GCE load balancer resources.</div>
        )}
        <GceProxyLoadBalancerEditor
          command={command}
          data={dataState.data}
          disabled={mode === 'edit'}
          onChange={setCommand}
        />
        {!!errors.length && (
          <div className="alert alert-danger gce-proxy-validation-errors">
            {errors.map((error) => (
              <div key={error}>{error}</div>
            ))}
          </div>
        )}
      </div>
      <div className="modal-footer">
        <button className="btn btn-default" disabled={submitting} onClick={props.dismissModal} type="button">
          Cancel
        </button>
        <SubmitButton
          isDisabled={!!errors.length || dataState.status === 'loading' || submitting}
          isFormSubmit={false}
          label={mode === 'pipeline' ? 'Done' : mode === 'edit' ? 'Update' : 'Create'}
          onClick={submit}
          submitting={submitting}
        />
      </div>
    </div>
  );
}

export const GceProxyLoadBalancerModal = Object.assign(GceProxyLoadBalancerModalComponent, {
  show: (props: IGceProxyLoadBalancerModalProps) =>
    ReactModal.show(GceProxyLoadBalancerModalComponent, props, { dialogClassName: 'modal-lg' }),
  supportsPipelineConfig: true,
});

function resolveProxyType(value: unknown): GceProxyLoadBalancerType {
  const type = String(value || '').toUpperCase();
  return type === 'INTERNAL' || type === 'SSL' || type === 'TCP' ? type : 'TCP';
}

function splitPorts(value: string | undefined): string[] {
  return String(value || '')
    .split(',')
    .map((port) => port.trim())
    .filter(Boolean);
}
