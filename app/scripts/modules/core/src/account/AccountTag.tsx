import * as React from 'react';
import { BindAll } from 'lodash-decorators';
import { ReactInjector } from 'core/reactShims';

export interface IAccountTagProps {
  account: string;
}

export interface IAccountTagState {
  isProdAccount: boolean;
}

@BindAll()
export class AccountTag extends React.Component<IAccountTagProps, IAccountTagState> {
  public state = { isProdAccount: false };

  constructor(props: IAccountTagProps) {
    super(props);

    ReactInjector.accountService.challengeDestructiveActions(props.account)
      .then(isProdAccount => this.setState({ isProdAccount }));
  }

  public render() {
    const { account } = this.props;
    const { isProdAccount } = this.state;
    return (
      <span className={`account-tag account-tag-${isProdAccount ? 'prod' : 'notprod'}`}>
        {account}
      </span>
    );
  }
}
