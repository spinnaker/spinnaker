import * as React from 'react';
import { BindAll } from 'lodash-decorators';

import { HelpField } from 'core/help/HelpField';

export interface IUserVerificationProps {
  label?: string;
  expectedValue: string;
  onValidChange: (isValid: boolean) => void;
}

export interface IUserVerificationState {
  value: string;
  matches: boolean;
}

@BindAll()
export class UserVerification extends React.Component<IUserVerificationProps, IUserVerificationState> {
  public state = { value: '', matches: false };

  private handleChange(evt: React.ChangeEvent<any>) {
    const { value } = evt.target;
    const { expectedValue } = this.props;

    const previousMatches = this.state.value === expectedValue;
    const matches = value === expectedValue;
    this.setState({ value, matches });

    if (matches !== previousMatches) {
      this.props.onValidChange(matches);
    }
  }

  public render() {
    const { label, expectedValue } = this.props;
    const { value, matches } = this.state;
    const className = `form-control input-sm highlight-pristine ${matches ? '' : 'ng-invalid'}`;
    const defaultLabel = (
      <>
        Type the name of the account (<span className="verification-text">{expectedValue}</span>) to continue
      </>
    );

    return (
      <div className="row verification user-verification">
        <div className="col-sm-12">
          <div className="form-inline">
            <div className="form-group">
              <div className="form-control-static">{label || defaultLabel}</div> <HelpField id="user.verification" />
              <input type="text" className={className} value={value} onChange={this.handleChange} />
            </div>
          </div>
        </div>
      </div>
    );
  }
}
