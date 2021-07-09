import React from 'react';

import { AccountTag } from '../../account';
import { Markdown } from '../../presentation';

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
