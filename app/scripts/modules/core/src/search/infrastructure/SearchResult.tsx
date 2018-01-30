import * as DOMPurify from 'dompurify';
import * as React from 'react';

import { AccountTag } from 'core/account';

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
        <span dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(displayName) }}/>
      </span>
    );
  }
}
