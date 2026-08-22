import React from 'react';
import { Modal } from 'react-bootstrap';

import { ModalClose } from '../../../../modal';
import type { IModalComponentProps } from '../../../../presentation';
import { ReactModal } from '../../../../presentation';

export interface ICiBuildParameter {
  key: string;
  value: string;
}

type IAddCiBuildParameterModalProps = IModalComponentProps<ICiBuildParameter>;

export class AddCiBuildParameterModal extends React.Component<IAddCiBuildParameterModalProps, ICiBuildParameter> {
  public state: ICiBuildParameter = {
    key: '',
    value: '',
  };

  public static show(): Promise<ICiBuildParameter> {
    return ReactModal.show(AddCiBuildParameterModal, {}, { dialogClassName: 'modal-md' });
  }

  private submit = (event: React.FormEvent<HTMLFormElement>): void => {
    event.preventDefault();
    const key = this.state.key.trim();
    if (key) {
      this.props.closeModal?.({ key, value: this.state.value });
    }
  };

  public render() {
    const { dismissModal } = this.props;
    return (
      <form role="form" className="container-fluid" noValidate onSubmit={this.submit}>
        <ModalClose dismiss={dismissModal || (() => {})} />
        <Modal.Header>
          <Modal.Title>Add Parameter</Modal.Title>
        </Modal.Header>
        <Modal.Body>
          <div className="form-group row">
            <div className="col-sm-3 sm-label-right">Key</div>
            <div className="col-sm-9">
              <input
                type="text"
                className="form-control input-sm"
                value={this.state.key}
                onChange={(event) => this.setState({ key: event.target.value })}
                placeholder="enter a parameter key"
                required
              />
            </div>
          </div>
          <div className="form-group row">
            <div className="col-sm-3 sm-label-right">Value</div>
            <div className="col-sm-9">
              <input
                type="text"
                className="form-control input-sm"
                value={this.state.value}
                onChange={(event) => this.setState({ value: event.target.value })}
                placeholder="enter a parameter value"
                required
              />
            </div>
          </div>
        </Modal.Body>
        <Modal.Footer>
          <button type="button" className="btn btn-default" onClick={dismissModal || (() => {})}>
            Cancel
          </button>
          <button type="submit" className="btn btn-primary" data-purpose="submit" disabled={!this.state.key.trim()}>
            <span className="far fa-check-circle" /> Add
          </button>
        </Modal.Footer>
      </form>
    );
  }
}
