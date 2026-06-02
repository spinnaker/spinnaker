import React from 'react';

import { DeployInitializer, ReactInjector, ReactModal, TaskMonitor } from '@spinnaker/core';

interface IGceCloneServerGroupModalProps {
  application: any;
  command: any;
  closeModal?: (command?: any) => void;
  dismissModal?: () => void;
  forPipelineConfig?: boolean;
  isNew?: boolean;
  title?: string;
}

const GLOBAL_LOAD_BALANCER_NAMES = 'global-load-balancer-names';
const REGIONAL_LOAD_BALANCER_NAMES = 'load-balancer-names';

function compactMetadata(metadata: Record<string, string[]>): Record<string, string> {
  return Object.keys(metadata).reduce((result: Record<string, string>, key: string) => {
    const values = Array.from(new Set(metadata[key].filter(Boolean)));
    if (values.length) {
      result[key] = values.join(',');
    }
    return result;
  }, {});
}

function metadataValues(value: any): string[] {
  if (Array.isArray(value)) {
    return value;
  }
  if (typeof value === 'string') {
    return value.split(',').map((item) => item.trim());
  }
  return [];
}

function loadBalancerName(loadBalancer: any): string {
  return loadBalancer.name || loadBalancer;
}

function buildLoadBalancerMetadata(command: any): Record<string, string> {
  const commandMetadata = command.loadBalancerMetadata || {};
  const explicitMetadata = {
    [GLOBAL_LOAD_BALANCER_NAMES]: metadataValues(commandMetadata[GLOBAL_LOAD_BALANCER_NAMES]),
    [REGIONAL_LOAD_BALANCER_NAMES]: metadataValues(commandMetadata[REGIONAL_LOAD_BALANCER_NAMES]),
  };

  if (explicitMetadata[GLOBAL_LOAD_BALANCER_NAMES].length || explicitMetadata[REGIONAL_LOAD_BALANCER_NAMES].length) {
    return compactMetadata(explicitMetadata);
  }

  const loadBalancerIndex = command.backingData?.filtered?.loadBalancerIndex || {};
  const metadata: Record<string, string[]> = {
    [GLOBAL_LOAD_BALANCER_NAMES]: [] as string[],
    [REGIONAL_LOAD_BALANCER_NAMES]: [] as string[],
  };

  (command.loadBalancers || []).forEach((loadBalancer: any) => {
    const name = loadBalancerName(loadBalancer);
    const loadBalancerDetails = loadBalancer.loadBalancerType ? loadBalancer : loadBalancerIndex[name];
    const listenerNames = (loadBalancerDetails?.listeners || []).map((listener: any) => listener.name);

    switch (loadBalancerDetails?.loadBalancerType) {
      case 'HTTP':
        metadata[GLOBAL_LOAD_BALANCER_NAMES].push(...listenerNames);
        break;
      case 'INTERNAL_MANAGED':
        metadata[REGIONAL_LOAD_BALANCER_NAMES].push(...listenerNames);
        break;
      case 'SSL':
      case 'TCP':
        metadata[GLOBAL_LOAD_BALANCER_NAMES].push(name);
        break;
      default:
        metadata[REGIONAL_LOAD_BALANCER_NAMES].push(name);
        break;
    }
  });

  if (command.backendServices && Object.keys(command.backendServices).length) {
    metadata['backend-service-names'] = Object.keys(command.backendServices).reduce(
      (backendServices: string[], loadBalancer: string) =>
        backendServices.concat(command.backendServices[loadBalancer]),
      [],
    );
  }

  return compactMetadata(metadata);
}

function transformCommand(command: any): any {
  const transformed = { ...command };

  transformed.instanceMetadata = {
    ...(command.instanceMetadata || {}),
    ...buildLoadBalancerMetadata(command),
  };
  transformed.tags = (command.tags || []).map((tag: any) => tag.value || tag);
  transformed.targetSize = command.capacity?.desired;
  if (command.enableAutoScaling && command.autoScalingPolicy) {
    transformed.capacity = {
      ...command.capacity,
      max: command.autoScalingPolicy.maxNumReplicas,
      min: command.autoScalingPolicy.minNumReplicas,
    };
  } else if (command.capacity) {
    transformed.capacity = {
      ...command.capacity,
      max: command.capacity.desired,
      min: command.capacity.desired,
    };
  }
  if (transformed.minCpuPlatform === '(Automatic)') {
    transformed.minCpuPlatform = '';
  }
  delete transformed.loadBalancerMetadata;
  delete transformed.securityGroups;
  return transformed;
}

export function canSubmitGceServerGroupCommand(command: any): boolean {
  const mode = command?.viewState?.mode;
  if (mode === 'editPipeline') {
    return true;
  }
  return Boolean(command?.source?.serverGroupName || command?.source?.asgName);
}

export class GceCloneServerGroupModal extends React.Component<IGceCloneServerGroupModalProps> {
  public state = {
    templateSelected: !this.props.command?.viewState?.requiresTemplateSelection,
  };

  public static show(props: IGceCloneServerGroupModalProps): Promise<any> {
    return ReactModal.show(GceCloneServerGroupModal, props, { dialogClassName: 'modal-lg' });
  }

  private taskMonitor = new TaskMonitor({
    application: this.props.application,
    title: this.props.title || 'Creating your server group',
    onTaskComplete: () => this.props.application.serverGroups.refresh(),
  });

  private submit = (): void => {
    if (!canSubmitGceServerGroupCommand(this.props.command)) {
      return;
    }
    const command = transformCommand(this.props.command);
    const mode = command.viewState?.mode;
    if (this.props.forPipelineConfig || mode === 'createPipeline' || mode === 'editPipeline') {
      this.props.closeModal?.(command);
      return;
    }
    this.taskMonitor.submit(() => ReactInjector.serverGroupWriter.cloneServerGroup(command, this.props.application));
  };

  public render(): JSX.Element {
    const { command, dismissModal, title } = this.props;
    const canSubmit = canSubmitGceServerGroupCommand(command);

    if (!this.state.templateSelected) {
      return (
        <DeployInitializer
          application={this.props.application}
          cloudProvider="gce"
          command={command}
          onDismiss={dismissModal || (() => undefined)}
          onTemplateSelected={() => this.setState({ templateSelected: true })}
          templateSelectionText={{
            additionalCopyText: '',
            copied: ['account', 'region', 'subnet', 'cluster name', 'load balancers', 'firewalls'],
            notCopied: ['deployment strategy'],
          }}
        />
      );
    }

    return (
      <div className="modal-content">
        <div className="modal-header">
          <button type="button" className="close" onClick={dismissModal}>
            <span>&times;</span>
          </button>
          <h3>{title || 'Create Server Group'}</h3>
        </div>
        <div className="modal-body">
          {!canSubmit && (
            <div className="alert alert-warning">
              Google server group creation without an existing template is unavailable until the React configuration
              wizard is ported. Select an existing server group template or cancel.
            </div>
          )}
          <div className="form-horizontal">
            <dl className="dl-horizontal dl-narrow">
              <dt>Account</dt>
              <dd>{command.credentials || '-'}</dd>
              <dt>Region</dt>
              <dd>{command.region || '-'}</dd>
              <dt>Instance Type</dt>
              <dd>{command.instanceType || '-'}</dd>
              <dt>Desired Capacity</dt>
              <dd>{command.capacity?.desired ?? '-'}</dd>
            </dl>
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-default" onClick={dismissModal} type="button">
            Cancel
          </button>
          <button className="btn btn-primary" disabled={!canSubmit} onClick={this.submit} type="button">
            Submit
          </button>
        </div>
      </div>
    );
  }
}
