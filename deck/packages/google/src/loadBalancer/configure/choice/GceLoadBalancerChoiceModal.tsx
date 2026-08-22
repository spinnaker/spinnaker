import React from 'react';

import type { Application, IModalComponentProps } from '@spinnaker/core';
import { ModalClose, noop, ReactModal } from '@spinnaker/core';

import type { GceLoadBalancerEditorMode, GceLoadBalancerType } from '../common';
import { GceProxyLoadBalancerModal } from '../common/GceProxyLoadBalancerModal';
import { GceHttpLoadBalancerModal } from '../http/GceHttpLoadBalancerModal';
import { GceNetworkLoadBalancerModal } from '../network/GceNetworkLoadBalancerModal';

import './GceLoadBalancerChoiceModal.less';

interface IGceLoadBalancerModal {
  show: (props: any) => Promise<any>;
}

export interface IGceLoadBalancerChoice {
  description: string;
  label: string;
  type: GceLoadBalancerType;
}

export const GCE_LOAD_BALANCER_CHOICES: IGceLoadBalancerChoice[] = [
  { type: 'NETWORK', label: 'Network', description: 'Regional pass-through network load balancer.' },
  { type: 'INTERNAL', label: 'Internal', description: 'Regional internal pass-through load balancer.' },
  { type: 'TCP', label: 'TCP', description: 'Global external TCP proxy load balancer.' },
  { type: 'SSL', label: 'SSL', description: 'Global external SSL proxy load balancer.' },
  { type: 'HTTP', label: 'HTTP(S)', description: 'Global external HTTP(S) load balancer.' },
  {
    type: 'INTERNAL_MANAGED',
    label: 'Internal HTTP(S)',
    description: 'Regional internal managed HTTP(S) load balancer.',
  },
];

export interface IGceLoadBalancerChoiceModalProps extends IModalComponentProps {
  app?: Application;
  application?: Application;
  forPipelineConfig?: boolean;
  isNew?: boolean;
  loadBalancer?: Record<string, any> | null;
}

interface IGceLoadBalancerChoiceModalState {
  selectedChoice: IGceLoadBalancerChoice | null;
}

export function getGceLoadBalancerModal(type: GceLoadBalancerType): IGceLoadBalancerModal {
  if (type === 'NETWORK') return GceNetworkLoadBalancerModal;
  if (type === 'HTTP' || type === 'INTERNAL_MANAGED') return GceHttpLoadBalancerModal;
  return GceProxyLoadBalancerModal;
}

export class GceLoadBalancerChoiceModal extends React.Component<
  IGceLoadBalancerChoiceModalProps,
  IGceLoadBalancerChoiceModalState
> {
  public static defaultProps: Partial<IGceLoadBalancerChoiceModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static supportsPipelineConfig = true;

  public static show(props: IGceLoadBalancerChoiceModalProps): Promise<any> {
    if (props.isNew === false && props.loadBalancer) {
      const type = getLoadBalancerType(props.loadBalancer.loadBalancerType);
      if (type) {
        const mode = props.forPipelineConfig ? 'pipeline' : 'edit';
        return getGceLoadBalancerModal(type).show(modalProps(props, type, mode));
      }
    }

    return ReactModal.show(GceLoadBalancerChoiceModal, props, {
      dialogClassName: 'create-pipeline-modal-overflow-visible modal-lg',
    });
  }

  public state: IGceLoadBalancerChoiceModalState = {
    selectedChoice: isBlockedEdit(this.props) ? null : GCE_LOAD_BALANCER_CHOICES[0],
  };

  public choiceSelected = (selectedChoice: IGceLoadBalancerChoice): void => {
    if (!isBlockedEdit(this.props)) {
      this.setState({ selectedChoice });
    }
  };

  private choose = (): void => {
    const { selectedChoice } = this.state;
    if (!selectedChoice || isBlockedEdit(this.props)) {
      return;
    }

    const { type } = selectedChoice;
    const mode = this.props.forPipelineConfig ? 'pipeline' : 'create';
    const result = getGceLoadBalancerModal(type).show(modalProps(this.props, type, mode));
    this.props.closeModal?.(result);
  };

  public render(): JSX.Element {
    const { selectedChoice } = this.state;
    const blocked = isBlockedEdit(this.props);
    const persistedType = this.props.loadBalancer?.loadBalancerType;
    const persistedTypeLabel = typeof persistedType === 'string' && persistedType ? `"${persistedType}"` : 'missing';
    return (
      <div className="gce-load-balancer-choice-modal">
        <ModalClose dismiss={this.props.dismissModal} />
        <div className="modal-header">
          <h4 className="modal-title">Select Type of Load Balancer</h4>
        </div>
        <div className="modal-body">
          {blocked && (
            <div className="alert alert-danger" role="alert">
              This load balancer cannot be edited because its persisted load balancer type is {persistedTypeLabel} and
              unsupported. No changes can be submitted.
            </div>
          )}
          <div className="card-choices">
            {GCE_LOAD_BALANCER_CHOICES.map((choice) => (
              <button
                aria-pressed={selectedChoice === choice}
                className={`card gce-load-balancer-choice-card ${selectedChoice === choice ? 'active' : ''}`}
                disabled={blocked}
                key={choice.type}
                onClick={() => this.choiceSelected(choice)}
                type="button"
              >
                <h3 className="load-balancer-label">{choice.label}</h3>
                <div>{choice.description}</div>
              </button>
            ))}
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-primary" disabled={blocked} onClick={this.choose} type="button">
            Configure Load Balancer <span className="glyphicon glyphicon-chevron-right" />
          </button>
        </div>
      </div>
    );
  }
}

function getLoadBalancerType(value: unknown): GceLoadBalancerType | undefined {
  return GCE_LOAD_BALANCER_CHOICES.find((choice) => choice.type === value)?.type;
}

function isBlockedEdit(props: IGceLoadBalancerChoiceModalProps): boolean {
  return props.isNew === false && !getLoadBalancerType(props.loadBalancer?.loadBalancerType);
}

function modalProps(
  props: IGceLoadBalancerChoiceModalProps,
  loadBalancerType: GceLoadBalancerType,
  mode: GceLoadBalancerEditorMode,
): Record<string, any> {
  const application = props.application || props.app;
  return {
    ...props,
    app: application,
    application,
    forPipelineConfig: mode === 'pipeline',
    isNew: mode === 'create',
    loadBalancer: props.loadBalancer || null,
    loadBalancerType,
    mode,
  };
}
