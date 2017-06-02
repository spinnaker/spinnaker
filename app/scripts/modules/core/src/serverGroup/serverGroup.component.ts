import { has } from 'lodash';
import { IComponentController, IComponentOptions, IFilterService, ITimeoutService, IScope, module } from 'angular';
import { StateService } from '@uirouter/angularjs';

import { CLUSTER_FILTER_MODEL, ClusterFilterModel } from 'core/cluster/filter/clusterFilter.model';
import { CLUSTER_FILTER_SERVICE } from 'core/cluster/filter/clusterFilter.service';
import { INSTANCES_COMPONENT } from './../instance/instances.component';
import { SERVER_GROUP_SEQUENCE_FILTER } from 'core/cluster/serverGroup.sequence.filter';
import { IInstance, IServerGroup } from 'core/domain';

export interface JenkinsViewModel {
  number: number;
  href?: string;
}

export interface ServerGroupViewModel {
  serverGroup: IServerGroup;
  serverGroupSequence: string[];
  jenkins: JenkinsViewModel;
  hasBuildInfo: boolean;
  instances: IInstance[];
  images?: string;
}

export class ServerGroupController implements IComponentController {
  public cluster: string;
  public serverGroup: IServerGroup;
  public application: string;
  public parentHeading: string;
  public hasLoadBalancers: boolean;
  public hasDiscovery: boolean;

  public headerIsSticky: () => boolean;
  public viewModel: ServerGroupViewModel;
  public sortFilter: any;

  private lastStringVal: string = null;

  constructor(public $state: StateService,
              private $scope: IScope,
              private $timeout: ITimeoutService,
              private $filter: IFilterService,
              private clusterFilterService: any,
              private MultiselectModel: any,
              private ClusterFilterModel: ClusterFilterModel,
              private serverGroupTransformer: any) {
    'ngInject';
  }

  public $onInit(): void {
    // TODO: Remove $scope. Keeping it here so we can use $watch for now.
    //       Eventually, there should be events fired when filters change.
    this.sortFilter = this.ClusterFilterModel.asFilterModel.sortFilter;
    this.$scope.$watch(() => this.sortFilter, () => this.setViewModel(), true);

    this.setViewModel();

    this.headerIsSticky = function() {
      if (!this.sortFilter.showAllInstances) {
        return false;
      }
      if (this.sortFilter.listInstances) {
        return this.viewModel.instances.length > 1;
      }
      return this.viewModel.instances.length > 20;
    };
  }

  public loadDetails(event: JQueryEventObject): void {
    this.$timeout(() => {
      if (event.isDefaultPrevented() || (event.originalEvent && (event.originalEvent.defaultPrevented || (event.originalEvent.target as any).href))) {
        return;
      }
      this.MultiselectModel.toggleServerGroup(this.serverGroup);
      event.preventDefault();
    });
  };

  private setViewModel(): void {
    const filteredInstances = this.serverGroup.instances.filter(i => this.clusterFilterService.shouldShowInstance(i));

    const serverGroup = this.serverGroup;

    const viewModel: ServerGroupViewModel = {
      serverGroup: serverGroup,
      serverGroupSequence: (<Function>this.$filter('serverGroupSequence'))(serverGroup.name),
      jenkins: null,
      hasBuildInfo: !!serverGroup.buildInfo,
      instances: filteredInstances,
    };

    if (serverGroup.buildInfo && serverGroup.buildInfo.jenkins &&
        (serverGroup.buildInfo.jenkins.host || serverGroup.buildInfo.jenkins.fullUrl || serverGroup.buildInfo.buildInfoUrl)) {
      const jenkins = serverGroup.buildInfo.jenkins;

      viewModel.jenkins = {
        number: jenkins.number
      };

      if (serverGroup.buildInfo.jenkins.host) {
        viewModel.jenkins.href = [jenkins.host + 'job', jenkins.name, jenkins.number, ''].join('/');
      }
      if (serverGroup.buildInfo.jenkins.fullUrl) {
        viewModel.jenkins.href = serverGroup.buildInfo.jenkins.fullUrl;
      }
      if (serverGroup.buildInfo.buildInfoUrl) {
        viewModel.jenkins.href = serverGroup.buildInfo.buildInfoUrl;
      }
    } else if (has(serverGroup, 'buildInfo.images')) {
      viewModel.images = serverGroup.buildInfo.images.join(', ');
    }

    const modelStringVal = JSON.stringify(viewModel, this.serverGroupTransformer.jsonReplacer);

    if (this.lastStringVal !== modelStringVal) {
      this.viewModel = viewModel;
      this.lastStringVal = modelStringVal;
    }

    viewModel.serverGroup.runningTasks = serverGroup.runningTasks;
    viewModel.serverGroup.runningExecutions = serverGroup.runningExecutions;

  }
}

class ServerGroupComponent implements IComponentOptions {
  public bindings: any = {
    cluster: '<',
    serverGroup: '<',
    application: '<',
    parentHeading: '<',
    hasLoadBalancers: '<',
    hasDiscovery: '<',
  };
  public controller: any = ServerGroupController;
  public templateUrl: string = require('./serverGroup.component.html');
}

export const SERVER_GROUP_COMPONENT = 'spinnaker.core.serverGroup.serverGroup.component';
module(SERVER_GROUP_COMPONENT, [
  CLUSTER_FILTER_SERVICE,
  SERVER_GROUP_SEQUENCE_FILTER,
  CLUSTER_FILTER_MODEL,
  require('../cluster/filter/multiselect.model'),
  INSTANCES_COMPONENT,
  require('../instance/instanceList.directive'),
  require('./serverGroup.transformer'),
])
.component('serverGroup', new ServerGroupComponent());
