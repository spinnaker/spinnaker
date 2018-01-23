import { IPromise } from 'angular';
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
  private static cache: {
    [account: string]: boolean|IPromise<boolean>;
  } = {};

  public state = { isProdAccount: false };
  private mounted = true;

  public componentWillUnmount(): void {
    this.mounted = false;
  }

  public componentDidMount() {
    this.updateAccount(this.props.account);
  }

  public componentWillReceiveProps(nextProps: IAccountTagProps): void {
    this.updateAccount(nextProps.account);
  }

  private updateAccount(account: string) {
    const { cache } = AccountTag;
    if (!cache.hasOwnProperty(account)) {
      cache[account] = ReactInjector.accountService.challengeDestructiveActions(account)
        .then(result => cache[account] = result);
    }

    const cachedVal: boolean | IPromise<boolean> = cache[account];

    if (typeof cachedVal === 'boolean') {
      this.setState({ isProdAccount: cachedVal })
    } else {
      cachedVal.then(isProdAccount => this.mounted && this.setState({ isProdAccount }));
    }
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
