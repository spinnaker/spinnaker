import { IController, IComponentOptions, IScope, module } from 'angular';

import { Application } from 'core/application/application.model';
import { IClusterSummary } from 'core/domain/ICluster';
import { CLUSTER_FILTER_MODEL, ClusterFilterModel } from '../filter/clusterFilter.model';

import './onDemandClusterPicker.component.less';

class OnDemandClusterPickerController implements IController {
  public application: Application;
  public availableClusters: IClusterSummary[];
  public lastSelection: string;
  public totalClusterCount: number;
  public optionTemplate: string = require('./onDemandOptionTemplate.html');

  constructor(private $scope: IScope, private clusterFilterModel: ClusterFilterModel) {
    'ngInject';
  }

  public $onInit(): void {
    this.setAvailableClusters();
    this.application.getDataSource('serverGroups').onRefresh(this.$scope, () => this.setAvailableClusters());
  }

  private setAvailableClusters(): void {
    this.totalClusterCount = this.application.getDataSource('serverGroups').clusters.length;
    const selectedClusters: string[] = Object.keys(this.clusterFilterModel.asFilterModel.sortFilter.clusters);
    this.availableClusters = this.application
      .getDataSource('serverGroups')
      .clusters.filter((cluster: IClusterSummary) => !selectedClusters.includes(this.makeKey(cluster)));
  }

  public selectCluster(cluster: IClusterSummary): void {
    this.lastSelection = undefined;
    this.clusterFilterModel.asFilterModel.sortFilter.clusters[this.makeKey(cluster)] = true;
    this.clusterFilterModel.asFilterModel.applyParamsToUrl();
    this.application.getDataSource('serverGroups').refresh();
  }

  private makeKey(cluster: IClusterSummary): string {
    return `${cluster.account}:${cluster.name}`;
  }
}

const template = `
    <input type="text"
           class="form-control"
           placeholder="{{$ctrl.totalClusterCount}} clusters found in this application. Select a cluster to view"
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
module(ON_DEMAND_CLUSTER_PICKER_COMPONENT, [CLUSTER_FILTER_MODEL]).component(
  'onDemandClusterPicker',
  onDemandClusterPickerComponent,
);
