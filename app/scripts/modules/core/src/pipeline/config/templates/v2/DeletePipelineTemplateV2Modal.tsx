import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { get } from 'lodash';
import { IPipelineTemplateV2 } from 'core/domain/IPipelineTemplateV2';
import { PipelineTemplateWriter } from '../PipelineTemplateWriter';

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
      err => this.setState({ deleteError: err }),
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
        <div className="modal-header">
          <h3>Really delete template {this.props.template.metadata.name}?</h3>
        </div>
        <div className="modal-body">
          Deleting a pipeline template will not work if there are any pipelines depending on it.
        </div>
        <div className="modal-footer">
          {deleteError && (
            <span className="delete-pipeline-template-modal__delete-error">{this.getDeleteErrorMessage()}</span>
          )}
          <button className="btn" onClick={this.props.onClose}>
            Cancel
          </button>
          <button className="btn btn-primary" onClick={this.deleteTemplate}>
            Delete
          </button>
        </div>
      </Modal>
    );
  }
}
