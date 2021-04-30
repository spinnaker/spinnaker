import React from 'react';
import { Button, Modal } from 'react-bootstrap';

import {
  CloudProviderRegistry,
  ILoadBalancerModalProps,
  ModalClose,
  ModalInjector,
  noop,
  ReactModal,
} from '@spinnaker/core';

import { AzureLoadBalancerTypes, IAzureLoadBalancer } from '../../utility';

export interface IAzureLoadBalancerChoiceModalState {
  choices: IAzureLoadBalancer[];
  selectedChoice: IAzureLoadBalancer;
}

export class AzureLoadBalancerChoiceModal extends React.Component<
  ILoadBalancerModalProps,
  IAzureLoadBalancerChoiceModalState
> {
  public static defaultProps: Partial<ILoadBalancerModalProps> = {
    closeModal: noop,
    dismissModal: noop,
  };

  public static show(props: ILoadBalancerModalProps): Promise<void> {
    return ReactModal.show(AzureLoadBalancerChoiceModal, {
      ...props,
      className: 'create-pipeline-modal-overflow-visible',
    });
  }

  constructor(props: ILoadBalancerModalProps) {
    super(props);
    this.state = {
      choices: AzureLoadBalancerTypes,
      selectedChoice: AzureLoadBalancerTypes[0],
    };
  }

  public choiceSelected(choice: IAzureLoadBalancer): void {
    this.setState({ selectedChoice: choice });
  }

  private choose = (): void => {
    this.close();
    const provider: any = CloudProviderRegistry.getValue('azure', 'loadBalancer');
    ModalInjector.modalService
      .open({
        templateUrl: provider.createLoadBalancerTemplateUrl,
        windowClass: 'modal-z-index',
        controller: `${provider.createLoadBalancerController} as ctrl`,
        size: 'lg',
        resolve: {
          application: () => this.props.app,
          loadBalancer: (): any => null,
          isNew: () => true,
          forPipelineConfig: () => false,
          loadBalancerType: () => this.state.selectedChoice,
        },
      })
      .result.catch(() => {});
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
