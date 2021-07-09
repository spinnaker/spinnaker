import React from 'react';
import { Button, Modal } from 'react-bootstrap';

import {
  CloudProviderRegistry,
  ILoadBalancerIncompatibility,
  ILoadBalancerModalProps,
  Markdown,
  ModalClose,
  noop,
  ReactModal,
} from '@spinnaker/core';

import { IAmazonLoadBalancerConfig, LoadBalancerTypes } from './LoadBalancerTypes';
import { AWSProviderSettings } from '../../aws.settings';

export interface IAmazonLoadBalancerChoiceModalState {
  choices: IAmazonLoadBalancerConfig[];
  selectedChoice: IAmazonLoadBalancerConfig;
}

export class AmazonLoadBalancerChoiceModal extends React.Component<
  ILoadBalancerModalProps,
  IAmazonLoadBalancerChoiceModalState
> {
  public static defaultProps: Partial<ILoadBalancerModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: ILoadBalancerModalProps): Promise<void> {
    return ReactModal.show(
      AmazonLoadBalancerChoiceModal,
      {
        ...props,
        className: 'create-pipeline-modal-overflow-visible modal-lg',
      },
      { bsSize: 'lg' },
    );
  }

  constructor(props: ILoadBalancerModalProps) {
    super(props);
    this.state = {
      choices: LoadBalancerTypes,
      selectedChoice: LoadBalancerTypes[0],
    };
  }

  public choiceSelected(choice: IAmazonLoadBalancerConfig): void {
    this.setState({ selectedChoice: choice });
  }

  private choose = (): void => {
    const { children, ...loadBalancerProps } = this.props;
    this.close();
    this.state.selectedChoice.component
      .show(loadBalancerProps)
      .then((loadBalancer) => {
        this.props.closeModal(loadBalancer);
      })
      .catch(() => {});
  };

  public close = (reason?: any): void => {
    this.props.dismissModal(reason);
  };

  private getIncompatibility(choice: IAmazonLoadBalancerConfig, cloudProvider: string): ILoadBalancerIncompatibility {
    const { loadBalancer = {} } = CloudProviderRegistry.getProvider(cloudProvider);
    const {
      incompatibleLoadBalancerTypes = [],
    }: { incompatibleLoadBalancerTypes: ILoadBalancerIncompatibility[] } = loadBalancer;

    return incompatibleLoadBalancerTypes.find((lb) => lb.type === choice.type);
  }

  private isIncompatibleWithAllProviders(choice: IAmazonLoadBalancerConfig) {
    const {
      app: { attributes },
    } = this.props;
    const { cloudProviders = [] }: { cloudProviders: string[] } = attributes;

    if (cloudProviders.length > 0) {
      return cloudProviders.every((cloudProvider: string) => !!this.getIncompatibility(choice, cloudProvider));
    }

    // If the list of cloud providers is empty, assume it is compatible by default
    return false;
  }

  public render() {
    const {
      app: { attributes },
    } = this.props;
    const { cloudProviders = [] }: { cloudProviders: string[] } = attributes;
    const { choices, selectedChoice } = this.state;

    // Remove any choices that are not compatible with all configured cloud providers
    const filteredChoices = choices.filter((choice) => !this.isIncompatibleWithAllProviders(choice));

    // Compute incompatibilities with the current selected choice so we can display a warning
    const incompatibilities: ILoadBalancerIncompatibility[] = cloudProviders
      .map((cloudProvider) => this.getIncompatibility(selectedChoice, cloudProvider))
      .filter((x: ILoadBalancerIncompatibility) => x);

    const loadBalancerWarning =
      AWSProviderSettings.createLoadBalancerWarnings &&
      AWSProviderSettings.createLoadBalancerWarnings[selectedChoice.type];

    return (
      <>
        <ModalClose dismiss={this.close} />
        <Modal.Header>
          <Modal.Title>Select Type of Load Balancer</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <div className="modal-body">
            <div className="card-choices">
              {filteredChoices.map((choice) => (
                <div
                  key={choice.type}
                  className={`card ${selectedChoice === choice ? 'active' : ''}`}
                  onClick={() => this.choiceSelected(choice)}
                >
                  <h3 className="load-balancer-label">{choice.label}</h3>
                  <h3>({choice.sublabel})</h3>
                  <div>{choice.description}</div>
                </div>
              ))}
            </div>
            <>
              {incompatibilities.length > 0 &&
                incompatibilities.map((incompatibility) => (
                  <div className="alert alert-warning">
                    <p>
                      <i className="fa fa-exclamation-triangle" /> {incompatibility.reason}
                    </p>
                  </div>
                ))}
            </>
            {!!loadBalancerWarning && (
              <div className="alert alert-warning">
                <p>
                  <i className="fa fa-exclamation-triangle" />
                  <Markdown message={loadBalancerWarning} style={{ display: 'inline-block', marginLeft: '2px' }} />
                </p>
              </div>
            )}
            <div className="load-balancer-description" />
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Button onClick={this.choose}>
            Configure Load Balancer <span className="glyphicon glyphicon-chevron-right" />
          </Button>
        </Modal.Footer>
      </>
    );
  }
}
