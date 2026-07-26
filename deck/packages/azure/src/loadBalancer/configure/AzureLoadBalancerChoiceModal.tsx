import React from 'react';
import { Button, Modal } from 'react-bootstrap';

import type { ILoadBalancerModalProps, ILoadBalancerUpsertCommand } from '@spinnaker/core';
import { ModalClose, noop, ReactModal } from '@spinnaker/core';

import { AzureLoadBalancerModal, getAzureLoadBalancerTypeChoice } from './AzureLoadBalancerModal';
import type { IAzureLoadBalancer } from '../../utility';
import { AzureLoadBalancerTypes } from '../../utility';

export interface IAzureLoadBalancerChoiceModalState {
  choices: IAzureLoadBalancer[];
  selectedChoice: IAzureLoadBalancer;
}

interface IAzureLoadBalancerChoiceModalProps extends Omit<ILoadBalancerModalProps, 'closeModal'> {
  closeModal?(loadBalancerCommand: ILoadBalancerUpsertCommand | Promise<ILoadBalancerUpsertCommand>): void;
}

export class AzureLoadBalancerChoiceModal extends React.Component<
  IAzureLoadBalancerChoiceModalProps,
  IAzureLoadBalancerChoiceModalState
> {
  public static defaultProps: Partial<IAzureLoadBalancerChoiceModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static supportsPipelineConfig = true;

  public static show(props: ILoadBalancerModalProps): Promise<ILoadBalancerUpsertCommand> {
    if ((props as any).isNew === false) {
      const application = (props as any).app || (props as any).application;

      return AzureLoadBalancerModal.show({
        ...props,
        app: application,
        application,
        loadBalancer: props.loadBalancer,
        isNew: false,
        forPipelineConfig: props.forPipelineConfig || false,
      });
    }

    const componentProps: IAzureLoadBalancerChoiceModalProps = {
      ...props,
      className: 'create-pipeline-modal-overflow-visible',
    };

    return ReactModal.show<IAzureLoadBalancerChoiceModalProps, ILoadBalancerUpsertCommand>(
      AzureLoadBalancerChoiceModal,
      componentProps,
    );
  }

  constructor(props: IAzureLoadBalancerChoiceModalProps) {
    super(props);
    this.state = {
      choices: AzureLoadBalancerTypes,
      selectedChoice: getAzureLoadBalancerTypeChoice(props.loadBalancer, (props as any).loadBalancerType),
    };
  }

  public choiceSelected(choice: IAzureLoadBalancer): void {
    this.setState({ selectedChoice: choice });
  }

  private choose = (): void => {
    const application = (this.props as any).app || (this.props as any).application;
    const configurePromise = AzureLoadBalancerModal.show({
      ...this.props,
      app: application,
      application,
      loadBalancer: this.props.loadBalancer || null,
      isNew: (this.props as any).isNew !== false,
      forPipelineConfig: this.props.forPipelineConfig || false,
      loadBalancerType: this.state.selectedChoice,
    });

    this.props.closeModal(configurePromise);
  };

  public close = (reason?: any): void => {
    this.props.dismissModal(reason);
  };

  public render() {
    const { choices, selectedChoice } = this.state;

    return (
      <>
        <ModalClose dismiss={this.close} />
        <Modal.Header>
          <Modal.Title>Select Type of Load Balancer</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <div className="modal-body">
            <div className="card-choices">
              {choices.map((choice) => (
                <div
                  key={choice.type}
                  className={`card ${selectedChoice === choice ? 'active' : ''}`}
                  onClick={() => this.choiceSelected(choice)}
                >
                  <h3 className="load-balancer-label">{choice.type}</h3>
                  <div>{choice.description}</div>
                </div>
              ))}
            </div>
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
