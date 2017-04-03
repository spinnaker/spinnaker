import {has} from 'lodash';
import {IComponentController, IComponentOptions, IFilterService, ITimeoutService, IRootScopeService, IScope, module} from 'angular';

import {SERVER_GROUP_SEQUENCE_FILTER} from 'core/cluster/serverGroup.sequence.filter';
import {CLUSTER_FILTER_SERVICE} from 'core/cluster/filter/clusterFilter.service';
import {ENTITY_UI_TAGS_COMPONENT} from 'core/entityTag/entityUiTags.component';
import {Instance} from 'core/domain/instance';
import {ServerGroup} from 'core/domain/serverGroup';

interface JenkinsViewModel {
  number: number;
  href?: string;
}

interface ViewModel {
  waypoint: string;
  serverGroup: ServerGroup;
  serverGroupSequence: string[];
  jenkins: JenkinsViewModel;
  hasBuildInfo: boolean;
  instances: Instance[];
  images?: string;
}

export class ServerGroupController implements IComponentController {
  public cluster: string;
  public serverGroup: ServerGroup;
  public application: string;
  public parentHeading: string;
  public hasLoadBalancers: boolean;
  public hasDiscovery: boolean;

  public headerIsSticky: () => boolean;
  public viewModel: ViewModel;
  public sortFilter: any;

  private lastStringVal: string = null;

  static get $inject(): string[] { return ['$rootScope', '$scope', '$timeout', '$filter', 'clusterFilterService', 'MultiselectModel', 'ClusterFilterModel', 'serverGroupTransformer']; }

  constructor(public $rootScope: IRootScopeService,
              private $scope: IScope,
              private $timeout: ITimeoutService,
              private $filter: IFilterService,
              private clusterFilterService: any,
              private MultiselectModel: any,
              private ClusterFilterModel: any, private serverGroupTransformer: any) {}

  public $onInit(): void {
    // TODO: Remove $scope. Keeping it here so we can use $watch for now.
    //       Eventually, there should be events fired when filters change.
    this.sortFilter = this.ClusterFilterModel.sortFilter;
    this.$scope.$watch(() => this.sortFilter, () => this.setViewModel(), true);

    this.setViewModel();

    this.headerIsSticky = function() {
      if (!this.$scope.sortFilter.showAllInstances) {
        return false;
      }
      if (this.$scope.sortFilter.listInstances) {
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

    let viewModel: ViewModel = {
      waypoint: [serverGroup.account, serverGroup.region, serverGroup.name].join(':'),
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

    let modelStringVal = JSON.stringify(viewModel, this.serverGroupTransformer.jsonReplacer);

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
  require('../cluster/filter/clusterFilter.model'),
  require('../cluster/filter/multiselect.model'),
  require('../instance/instances.directive'),
  require('../instance/instanceList.directive'),
  require('./serverGroup.transformer'),
  ENTITY_UI_TAGS_COMPONENT,
])
.component('serverGroup', new ServerGroupComponent());
