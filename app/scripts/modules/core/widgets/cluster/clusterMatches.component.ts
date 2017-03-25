import {module, IComponentOptions} from 'angular';

export interface IClusterMatch {
  name: string;
  account: string;
  regions: string[];
}

class ClusterMatchesComponent implements IComponentOptions {

  public bindings: any = {
    matches: '=',
    index: '='
  };
  public template = `
    <div ng-repeat="match in $ctrl.matches[$ctrl.index]">
      <account-tag account="match.account"></account-tag>
      {{match.name}}
      <i ng-if="match.regions">in {{match.regions.join(', ')}}</i>
    </div>
    <div ng-if="$ctrl.matches[$ctrl.index].length === 0">(no matches)</div>
  `;
}

export const CLUSTER_MATCHES_COMPONENT = 'spinnaker.core.widget.cluster.clusterMatches.component';
module(CLUSTER_MATCHES_COMPONENT, [require('core/account/accountTag.directive.js')])
  .component('clusterMatches', new ClusterMatchesComponent());
