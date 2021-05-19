import React from 'react';
import { AccountService } from './AccountService';

export interface IAccountTagProps {
  account: string;
  className?: string;
}

export interface IAccountTagState {
  isProdAccount: boolean;
}

export class AccountTag extends React.Component<IAccountTagProps, IAccountTagState> {
  private static cache: {
    [account: string]: boolean | PromiseLike<boolean>;
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
      cache[account] = AccountService.challengeDestructiveActions(account).then(
        (result) => (cache[account] = !!result),
      );
    }

    const cachedVal: boolean | PromiseLike<boolean> = cache[account];

    if (typeof cachedVal === 'boolean') {
      this.setState({ isProdAccount: cachedVal });
    } else {
      cachedVal.then((isProdAccount) => this.mounted && this.setState({ isProdAccount }));
    }
  }

  public render() {
    const { account, className } = this.props;
    const { isProdAccount } = this.state;
    const shouldShowTitle = className === 'account-tag-wrapper';
    return (
      <span className={`account-tag account-tag-${isProdAccount ? 'prod' : 'notprod'} ${className || ''}`}>
        <span className="account-tag-name" title={shouldShowTitle ? account : null}>
          {account}
        </span>
      </span>
    );
  }
}
