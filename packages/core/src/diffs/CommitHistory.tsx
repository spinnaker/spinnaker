import { DateTime } from 'luxon';
import * as React from 'react';

export interface ICommit {
  authorDisplayName: string;
  commitUrl: string;
  displayId: string;
  id: string;
  message: string;
  timestamp: number;
}

export interface ICommitHistoryProps {
  commits: ICommit[];
}

export const CommitHistory = ({ commits }: ICommitHistoryProps) => (
  <div>
    <table className="table table-condensed">
      <tbody>
        <tr>
          <th>Date</th>
          <th>Commit</th>
          <th>Message</th>
          <th>Author</th>
        </tr>
        {commits.map((commit) => (
          <tr key={commit.id}>
            <td>{DateTime.fromMillis(commit.timestamp).toFormat('MM/dd')}</td>
            <td>
              <a target="_blank" href={commit.commitUrl}>
                {commit.displayId}
              </a>
            </td>
            <td>{commit.message.slice(0, 50)}</td>
            <td>{commit.authorDisplayName || 'N/A'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  </div>
);
