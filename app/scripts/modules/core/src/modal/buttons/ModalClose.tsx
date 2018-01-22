import * as React from 'react';

export interface IModalCloseProps {
  dismiss: () => void;
}

export class ModalClose extends React.Component<IModalCloseProps> {
  public render() {
    return (
      <div className="modal-close close-button pull-right">
        <button className="link" onClick={this.props.dismiss}>
          <span className="glyphicon glyphicon-remove"/>
        </button>
      </div>
    );
  }
}
