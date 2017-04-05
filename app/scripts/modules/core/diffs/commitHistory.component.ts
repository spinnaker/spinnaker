import {module} from 'angular';

import {IComponentOptions} from 'angular';

export interface ICommit {
  authorDisplayName: string;
  commitUrl: string;
  displayId: string;
  id: string;
  message: string;
  timestamp: number;
}

class CommitHistoryComponent implements IComponentOptions {
  public bindings: any = {
    commits: '<'
  };
  public template = `
  <div ng-if="$ctrl.commits && $ctrl.commits.length">
    <table class="table table-condensed">
      <tr><th>Date</th><th>Commit</th><th>Message</th><th>Author</th></tr>
      <tr ng-repeat="commit in $ctrl.commits">
        <td>{{commit.timestamp | date:'MM/dd'}}</td>
        <td><a target="_blank" href="{{commit.commitUrl}}">{{commit.displayId}}</a></td>
        <td>{{commit.message | limitTo: 50}}</td>
        <td>{{commit.authorDisplayName || 'N/A'}}</td>
      </tr>
    </table>
  </div>
  `;
}

export const COMMIT_HISTORY_COMPONENT = 'spinnaker.diffs.commit.history.component';
module(COMMIT_HISTORY_COMPONENT, []).component('commitHistory', new CommitHistoryComponent());
