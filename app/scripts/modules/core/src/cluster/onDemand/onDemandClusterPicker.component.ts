import { IComponentOptions, IController, IScope, module } from 'angular';
import { Application } from '../../application/application.model';
import { IClusterSummary } from '../../domain/ICluster';
import { ClusterState } from '../../state';

import './onDemandClusterPicker.component.less';

class OnDemandClusterPickerController implements IController {
  public application: Application;
  public availableClusters: IClusterSummary[];
  public lastSelection: string;
  public totalClusterCount: number;
  public optionTemplate: string = require('./onDemandOptionTemplate.html');

  public static $inject = ['$scope'];
  constructor(private $scope: IScope) {}

  public $onInit(): void {
    this.setAvailableClusters();
    this.application.getDataSource('serverGroups').onRefresh(this.$scope, () => this.setAvailableClusters());
  }

  private setAvailableClusters(): void {
    this.totalClusterCount = this.application.getDataSource('serverGroups').clusters.length;
    const selectedClusters: string[] = Object.keys(ClusterState.filterModel.asFilterModel.sortFilter.clusters);
    this.availableClusters = this.application
      .getDataSource('serverGroups')
      .clusters.filter((cluster: IClusterSummary) => !selectedClusters.includes(this.makeKey(cluster)));
  }

  public selectCluster(cluster: IClusterSummary): void {
    this.lastSelection = undefined;
    ClusterState.filterModel.asFilterModel.sortFilter.clusters[this.makeKey(cluster)] = true;
    ClusterState.filterModel.asFilterModel.applyParamsToUrl();
    this.application.getDataSource('serverGroups').refresh();
  }

  private makeKey(cluster: IClusterSummary): string {
    return `${cluster.account}:${cluster.name}`;
  }
}

const template = `
    <h4>{{$ctrl.totalClusterCount}} clusters found in this application</h4>
    <p><strong>Not all clusters are shown.</strong> Select or enter a cluster name below to view:</p>
    <input type="text"
           class="form-control"
           placeholder="Enter cluster name here"
           ng-model="$ctrl.lastSelection"
           uib-typeahead="cluster for cluster in $ctrl.availableClusters | filter: $viewValue | limitTo: 50"
           typeahead-min-length="0"
           typeahead-focus-on-select="false"
           typeahead-on-select="$ctrl.selectCluster($item)"
           typeahead-template-url="{{$ctrl.optionTemplate}}"
           />
`;

const onDemandClusterPickerComponent: IComponentOptions = {
  template,
  controller: OnDemandClusterPickerController,
  bindings: {
    application: '<',
  },
};

export const ON_DEMAND_CLUSTER_PICKER_COMPONENT = 'spinnaker.core.cluster.onDemandClusterPicker.component';
module(ON_DEMAND_CLUSTER_PICKER_COMPONENT, []).component('onDemandClusterPicker', onDemandClusterPickerComponent);
