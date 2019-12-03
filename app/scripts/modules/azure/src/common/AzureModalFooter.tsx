import React from 'react';

import { UserVerification } from '@spinnaker/core';

export interface IAzureModalFooterProps {
  onSubmit: () => void;
  onCancel: () => void;
  isValid: boolean;
  account: string;
}

export interface IAzureModalFooterState {
  verified: boolean;
}

export class AzureModalFooter extends React.Component<IAzureModalFooterProps, IAzureModalFooterState> {
  constructor(props: any) {
    super(props);
  }

  public state = { verified: false };

  private handleVerification = (verified: boolean) => {
    this.setState({ verified });
  };

  public render() {
    const { onSubmit, onCancel, isValid, account } = this.props;
    const { verified } = this.state;

    return (
      <div className="modal-footer">
        {<UserVerification expectedValue={account} onValidChange={this.handleVerification} />}
        <button className="btn btn-default" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="btn btn-primary" onClick={onSubmit} disabled={!isValid || !verified}>
          Submit
        </button>
      </div>
    );
  }
}
