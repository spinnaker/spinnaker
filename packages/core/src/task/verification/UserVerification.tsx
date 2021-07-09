import React from 'react';

import { AccountService } from '../../account';
import { HelpField } from '../../help/HelpField';
import { Markdown } from '../../presentation';

const { useEffect, useState } = React;

export interface IUserVerificationProps {
  label?: string;
  onValidChange: (isValid: boolean) => void;

  // Provide one of:
  expectedValue?: string;
  account?: string;
}

export function UserVerification(props: IUserVerificationProps) {
  const { label, account } = props;
  const [value, setValue] = useState('');

  if (props.account && props.expectedValue) {
    throw new Error("Supply either the 'account' or the 'expectedValue' prop, but not both");
  }

  const expectedValue = props.expectedValue || account;
  const matches = value === expectedValue;

  const [challengeDestructiveActions, setChallengeDestructiveActions] = useState(true);
  useEffect(() => {
    account && AccountService.challengeDestructiveActions(account).then(setChallengeDestructiveActions);
  }, [account]);

  const hideComponent = account && !challengeDestructiveActions;

  useEffect(() => props.onValidChange(hideComponent || matches), [matches, hideComponent]);

  const defaultLabel = (
    <>
      Type the name of the account (<span className="verification-text">{expectedValue}</span>) to continue
    </>
  );

  const className = `form-control input-sm highlight-pristine ${matches ? '' : 'ng-invalid'}`;
  return hideComponent ? null : (
    <div className="row verification user-verification">
      <div className="col-sm-12">
        <div className="form-inline">
          <div className="form-group">
            <div className="form-control-static">{label ? <Markdown tag="span" message={label} /> : defaultLabel}</div>{' '}
            <HelpField id="user.verification" />
            <input type="text" className={className} value={value} onChange={(evt) => setValue(evt.target.value)} />
          </div>
        </div>
      </div>
    </div>
  );
}
