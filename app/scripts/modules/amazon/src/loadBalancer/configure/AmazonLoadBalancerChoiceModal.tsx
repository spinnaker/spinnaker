import * as React from 'react';
import { Button, Modal } from 'react-bootstrap';
import { BindAll } from 'lodash-decorators';

import { IAmazonLoadBalancerConfig, IAmazonLoadBalancerModalProps, LoadBalancerTypes } from './LoadBalancerTypes';

export interface IAmazonLoadBalancerChoiceModalState {
  choices: IAmazonLoadBalancerConfig[];
  configuring: boolean;
  selectedChoice: IAmazonLoadBalancerConfig;
}

@BindAll()
export class AmazonLoadBalancerChoiceModal extends React.Component<IAmazonLoadBalancerModalProps, IAmazonLoadBalancerChoiceModalState> {
  constructor(props: IAmazonLoadBalancerModalProps) {
    super(props);
    this.state = {
      choices: LoadBalancerTypes,
      configuring: false,
      selectedChoice: LoadBalancerTypes[0],
    };
  }

  public choiceSelected(choice: IAmazonLoadBalancerConfig): void {
    this.setState({ selectedChoice: choice });
  }

  public choose(): void {
    this.setState({ configuring: true });
  }

  public close(): void {
    this.setState({ configuring: false, selectedChoice: this.state.choices[0] });
    this.props.showCallback(false);
  }

  public render() {
    const { choices, configuring, selectedChoice } = this.state;

    if (configuring) {
      const { app, forPipelineConfig, loadBalancer, onComplete } = this.props;
      const ConfigureLoadBalancerModal = selectedChoice.component;
      return (
        <ConfigureLoadBalancerModal
          app={app}
          forPipelineConfig={forPipelineConfig}
          loadBalancer={loadBalancer}
          onComplete={onComplete}
          show={true}
          showCallback={this.close}
        />
      );
    }

    return (
      <Modal show={this.props.show} onHide={this.close} className="create-pipeline-modal-overflow-visible" backdrop="static">
        <Modal.Header closeButton={true}>
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
                  <h3 className="load-balancer-label">{choice.label}</h3>
                  <h3>({choice.sublabel})</h3>
                  <div>
                    {choice.description}
                  </div>
                </div>
              ))}
            </div>
            <div className="load-balancer-description"/>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <Button onClick={this.choose}>Configure Load Balancer <span className="glyphicon glyphicon-chevron-right"/></Button>
        </Modal.Footer>
      </Modal>
    );
  }
}
