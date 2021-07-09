import React from 'react';
import { ModalFooter } from 'react-bootstrap';
import { map, take } from 'rxjs/operators';

import { AccountService, IAccountDetails, UserVerification } from '@spinnaker/core';

export interface IAwsModalFooterProps {
  account: string;
  isValid?: boolean;
  onCancel: () => void;
  onSubmit: () => void;
}

export interface IAwsModalFooterState {
  verified: boolean;
  requireVerification: boolean;
}

export class AwsModalFooter extends React.Component<IAwsModalFooterProps, IAwsModalFooterState> {
  public static defaultProps: Partial<IAwsModalFooterProps> = {
    isValid: true,
  };

  public state = { verified: false, requireVerification: false };

  public componentDidMount() {
    AccountService.accounts$
      .pipe(
        take(1),
        map((accounts: IAccountDetails[]) => accounts.find((account) => account.name === this.props.account)),
      )
      .subscribe((account: IAccountDetails) => {
        this.setState({ requireVerification: !!account && account.challengeDestructiveActions });
      });
  }

  private handleVerification = (verified: boolean) => {
    this.setState({ verified });
  };

  public render() {
    const { account, onCancel, onSubmit, isValid } = this.props;
    const { verified, requireVerification } = this.state;
    const handleSubmit = () => {
      this.props.onSubmit();
      return false;
    };

    return (
      <ModalFooter>
        <form onSubmit={handleSubmit}>
          {requireVerification && <UserVerification expectedValue={account} onValidChange={this.handleVerification} />}
        </form>

        <button className="btn btn-default" onClick={onCancel}>
          Cancel
        </button>
        <button
          type="submit"
          className="btn btn-primary"
          onClick={onSubmit}
          disabled={!isValid || (requireVerification && !verified)}
        >
          Submit
        </button>
      </ModalFooter>
    );
  }
}
