import { AccountTag } from 'core/account';
import { Markdown } from 'core/presentation';
import React from 'react';

import './searchResult.less';

export interface ISearchResultProps {
  account?: string;
  displayName: string;
}

export class SearchResult extends React.Component<ISearchResultProps> {
  public render() {
    const { displayName, account } = this.props;

    return (
      <span className="search-result">
        {account && <AccountTag account={account} />}
        <Markdown tag="span" message={displayName} />
      </span>
    );
  }
}
