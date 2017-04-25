import * as React from 'react';
import {Modal, Button} from 'react-bootstrap';
import {IPipelineTemplate} from './pipelineTemplate.service';

interface IState {}

interface IProps {
  template: IPipelineTemplate
  show: boolean;
  showCallback: (show: boolean) => null;
}

export class ConfigurePipelineTemplateModal extends React.Component<IProps, IState> {

  private close = (): void => {
    this.props.showCallback(false);
  };

  public render() {
    return (
      <Modal show={this.props.show} onHide={this.close}>
        <Modal.Body>
          <pre>{JSON.stringify(this.props.template || {}, null, 2)}</pre>
        </Modal.Body>
        <Modal.Footer>
          <Button onClick={this.close}>Cancel</Button>
        </Modal.Footer>
      </Modal>
    );
  }
}
