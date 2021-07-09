import { get } from 'lodash';
import React from 'react';
import { Modal } from 'react-bootstrap';

import { PipelineTemplateWriter } from '../PipelineTemplateWriter';
import { IPipelineTemplateV2 } from '../../../../domain/IPipelineTemplateV2';
import { ModalClose } from '../../../../modal';

import './DeletePipelineTemplateV2Modal.less';

export interface IDeletePipelineTemplateV2ModalProps {
  template: IPipelineTemplateV2;
  onClose: () => void;
}

export interface IDeletePipelineTemplateV2ModalState {
  deleteError?: Error;
}

export class DeletePipelineTemplateV2Modal extends React.Component<
  IDeletePipelineTemplateV2ModalProps,
  IDeletePipelineTemplateV2ModalState
> {
  constructor(props: IDeletePipelineTemplateV2ModalProps) {
    super(props);
    this.state = {
      deleteError: null,
    };
  }

  private deleteTemplate = () => {
    PipelineTemplateWriter.deleteTemplate(this.props.template).then(
      () => this.props.onClose(),
      (err) => this.setState({ deleteError: err }),
    );
  };

  private getDeleteErrorMessage(): string {
    const { deleteError } = this.state;
    if (this.state.deleteError) {
      const message = get(deleteError, 'data.message', get(deleteError, 'message', ''));
      if (message) {
        return message;
      }
      return 'An unknown error occurred while deleting this template';
    }
    return '';
  }

  public render() {
    const { deleteError } = this.state;
    return (
      <Modal show={true} dialogClassName="modal-lg" onHide={() => {}}>
        <ModalClose dismiss={this.props.onClose} />
        <Modal.Header>
          <Modal.Title>Really delete template {this.props.template.metadata.name}?</Modal.Title>
        </Modal.Header>
        <Modal.Body>Deleting a pipeline template will not work if there are any pipelines depending on it.</Modal.Body>
        <Modal.Footer>
          {deleteError && (
            <span className="delete-pipeline-template-modal__delete-error">{this.getDeleteErrorMessage()}</span>
          )}
          <button className="btn btn-default" onClick={this.props.onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={this.deleteTemplate}>
            Delete
          </button>
        </Modal.Footer>
      </Modal>
    );
  }
}
