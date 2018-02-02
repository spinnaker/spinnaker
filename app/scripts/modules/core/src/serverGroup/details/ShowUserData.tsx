import * as React from 'react';
import { Modal } from 'react-bootstrap';
import { BindAll } from 'lodash-decorators';

export interface IShowUserDataProps {
  serverGroupName: string;
  title?: string;
  userData: any;
}

export interface IShowUserDataState {
  show: boolean;
}

@BindAll()
export class ShowUserData extends React.Component<IShowUserDataProps, IShowUserDataState> {

  constructor(props: IShowUserDataProps) {
    super(props);
    this.state = {
      show: false,
    };
  }

  private close(): void {
    this.setState({ show: false });
  }

  private open(): void {
    this.setState({ show: true });
  }

  public render() {
    const { serverGroupName, title, userData } = this.props;
    const { show } = this.state;

    return (
      <span>
        <a className="clickable" onClick={this.open}>Show User Data</a>
        <Modal show={show} onHide={this.close}>
          <Modal.Header closeButton={true}>
            <h3>{title || 'User Data'} for {serverGroupName}</h3>
          </Modal.Header>
          <Modal.Body>
            <div className="modal-body">
              <textarea readOnly={true} rows={15} className="code">{userData}</textarea>
            </div>
          </Modal.Body>
          <Modal.Footer>
            <button
              className="btn btn-default"
              onClick={this.close}
            >
              Close
            </button>
          </Modal.Footer>
        </Modal>
      </span>
    );
  }
}
