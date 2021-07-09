import React from 'react';

import { AccountTag } from '../../account';

import { IClusterMatch } from './clusterMatches.component';

export interface IClusterMatchesProps {
  matches: IClusterMatch[];
}

export class ClusterMatches extends React.Component<IClusterMatchesProps> {
  public render() {
    const { matches } = this.props;
    if (!matches || !matches.length) {
      return <div>(no matches)</div>;
    }

    return (
      <ul className="nostyle">
        {matches.map((match: IClusterMatch, index: number) => (
          <li key={index}>
            <AccountTag account={match.account} />
            <span className="break-word">{match.name}</span>
            {match.regions && match.regions.length && <i> in {match.regions.join(', ')}</i>}
          </li>
        ))}
      </ul>
    );
  }
}
