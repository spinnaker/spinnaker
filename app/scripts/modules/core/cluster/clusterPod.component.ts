import {module, IComponentController, IComponentOptions} from 'angular';

import {Application} from 'core/application/application.model';
import {URL_BUILDER_SERVICE, UrlBuilderService} from 'core/navigation/urlBuilder.service';
import {SERVER_GROUP_COMPONENT} from 'core/serverGroup/serverGroup.component';

class ClusterPodController implements IComponentController {
  public grouping: any;
  public sortFilter: any;
  public application: Application;
  public parentHeading: string;
  public host: string;
  public permalink: string;

  static get $inject(): string[] { return ['urlBuilderService']; }
  constructor(private urlBuilderService: UrlBuilderService) {}

  public $onInit(): void {
    // using location.host here b/c it provides the port, $location.host() does not.
    // Easy way to get this to work in both dev(where we have a port) and prod(where we do not).
    this.host = location.host;
    this.permalink = this.urlBuilderService.buildFromMetadata(
      {
        type: 'clusters',
        application: this.application.name,
        cluster: this.grouping.heading,
        account: this.parentHeading
      }
    );
  }
}

class ClusterPodComponent implements IComponentOptions {
  public bindings: any = {
    grouping: '<',
    sortFilter: '<',
    application: '<',
    parentHeading: '<'
  };
  public controller: any = ClusterPodController;
  public templateUrl = require('./clusterPod.html');
}

export const CLUSTER_POD_COMPONENT = 'spinnaker.core.cluster.pod.component';
module(CLUSTER_POD_COMPONENT, [
  URL_BUILDER_SERVICE,
  SERVER_GROUP_COMPONENT
])
  .component('clusterPod', new ClusterPodComponent());
