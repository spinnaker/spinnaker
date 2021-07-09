import React from 'react';
import { Modal } from 'react-bootstrap';

import { decodeUnicodeBase64 } from '../../utils/unicodeBase64';

export interface IShowUserDataProps {
  serverGroupName: string;
  title?: string;
  userData: any;
}

export interface IShowUserDataState {
  show: boolean;
  decodeAsText: boolean;
}

export class ShowUserData extends React.Component<IShowUserDataProps, IShowUserDataState> {
  constructor(props: IShowUserDataProps) {
    super(props);
    this.state = {
      show: false,
      decodeAsText: true,
    };
  }

  private close = () => {
    this.setState({ show: false });
  };

  private open = () => {
    this.setState({ show: true });
  };

  private onDecodeChange = ({ target }: React.ChangeEvent<HTMLInputElement>) => {
    this.setState({ decodeAsText: target.checked });
  };

  public render() {
    const { serverGroupName, title, userData } = this.props;
    const { show, decodeAsText } = this.state;

    return (
      <span>
        <a className="clickable" onClick={this.open}>
          Show User Data
        </a>
        <Modal show={show} onHide={this.close}>
          <Modal.Header closeButton={true}>
            <h3>
              {title || 'User Data'} for {serverGroupName}
            </h3>
          </Modal.Header>
          <Modal.Body>
            <>
              <textarea
                className="code"
                readOnly={true}
                rows={15}
                value={decodeAsText ? decodeUnicodeBase64(userData) : userData}
              />
              <div className="checkbox" style={{ marginBottom: '0' }}>
                <label>
                  <input type="checkbox" checked={decodeAsText} onChange={this.onDecodeChange} />
                  Decode as text
                </label>
              </div>
            </>
          </Modal.Body>
          <Modal.Footer>
            <button className="btn btn-default" onClick={this.close}>
              Close
            </button>
          </Modal.Footer>
        </Modal>
      </span>
    );
  }
}
