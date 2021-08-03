import { DateTime } from 'luxon';
import * as React from 'react';

export interface ICommit {
  authorDisplayName: string;
  commitUrl: string;
  displayId: string;
  id: string;
  message: string;
  timestamp: number | string;
}

export interface ICommitHistoryProps {
  commits: ICommit[];
}

export const CommitHistory = ({ commits }: ICommitHistoryProps) => {
  const formatDate = (timestamp: string | number) => {
    return typeof timestamp === 'string' ? DateTime.fromISO(timestamp).toFormat('MM/dd') : DateTime.fromMillis(timestamp).toFormat('MM/dd');
  };
  
  return (
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
            <td>{formatDate(commit.timestamp)}</td>
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
)};
