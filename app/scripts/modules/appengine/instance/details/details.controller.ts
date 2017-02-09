import {module, IPromise, IQService} from 'angular';
import {flattenDeep, cloneDeep} from 'lodash';

import {Application} from 'core/application/application.model';
import {CONFIRMATION_MODAL_SERVICE, ConfirmationModalService} from 'core/confirmationModal/confirmationModal.service';
import {INSTANCE_READ_SERVICE, InstanceReader} from 'core/instance/instance.read.service';
import {INSTANCE_WRITE_SERVICE, InstanceWriter} from 'core/instance/instance.write.service';
import {IAppengineInstance} from 'appengine/domain/index';

interface InstanceFromStateParams {
  instanceId: string;
}

interface InstanceContainer {
  account: string;
  region: string;
  instances: IAppengineInstance[];
}

class AppengineInstanceDetailsController {
  public state = {loading: true};
  public instance: IAppengineInstance;
  public instanceIdNotFound: String;
  public upToolTip = 'An App Engine instance is \'Up\' if a load balancer is directing traffic to its server group.';
  public outOfServiceToolTip = `
    An App Engine instance is 'Out Of Service' if no load balancers are directing traffic to its server group.`;

  static get $inject() {
    return ['$scope', '$state', '$q', 'app', 'instanceReader', 'instanceWriter', 'confirmationModalService', 'instance'];
  }

  constructor(private $q: IQService,
              private app: Application,
              private instanceReader: InstanceReader,
              private instanceWriter: InstanceWriter,
              private confirmationModalService: ConfirmationModalService,
              instance: InstanceFromStateParams) {
    this.app.ready()
      .then(() => this.retrieveInstance(instance))
      .then((instanceDetails) => {
        this.instance = instanceDetails;
        this.state.loading = false;
      })
      .catch(() => {
        this.instanceIdNotFound = instance.instanceId;
        this.state.loading = false;
      });
  }

  public terminateInstance(): void {
    let instance = cloneDeep(this.instance) as any;
    let shortName = `${this.instance.name.substring(0, 10)}...`;
    instance.placement = {};
    instance.instanceId = instance.name;

    let taskMonitor = {
      application: this.app,
      title: 'Terminating ' + shortName,
      onTaskComplete: function() {
        if (this.$state.includes('**.instanceDetails', {instanceId: instance.name})) {
          this.$state.go('^');
        }
      }
    };

    let submitMethod = () => {
      return this.instanceWriter.terminateInstance(instance, this.app, {cloudProvider: 'appengine'});
    };

    this.confirmationModalService.confirm({
      header: 'Really terminate ' + shortName + '?',
      buttonText: 'Terminate ' + shortName,
      account: instance.account,
      taskMonitorConfig: taskMonitor,
      submitMethod: submitMethod
    });
  }

  private retrieveInstance(instance: InstanceFromStateParams): IPromise<IAppengineInstance> {
    let instanceLocatorPredicate = (dataSource: InstanceContainer) => {
      return dataSource.instances.some((possibleMatch) => possibleMatch.id === instance.instanceId);
    };

    let dataSources: InstanceContainer[] = flattenDeep([
      this.app.getDataSource('serverGroups').data,
      this.app.getDataSource('loadBalancers').data,
      this.app.getDataSource('loadBalancers').data.map((loadBalancer) => loadBalancer.serverGroups),
    ]);

    let instanceContainer = dataSources.find(instanceLocatorPredicate);

    if (instanceContainer) {
      return this.instanceReader
        .getInstanceDetails(instanceContainer.account, instanceContainer.region, instance.instanceId)
        .then((instanceDetails: IAppengineInstance) => {
          instanceDetails.account = instanceContainer.account;
          instanceDetails.region = instanceContainer.region;
          return instanceDetails;
        });
    } else {
      return this.$q.reject();
    }
  }
}

export const APPENGINE_INSTANCE_DETAILS_CTRL = 'spinnaker.appengine.instanceDetails.controller';

module(APPENGINE_INSTANCE_DETAILS_CTRL, [
  INSTANCE_READ_SERVICE,
  INSTANCE_WRITE_SERVICE,
  CONFIRMATION_MODAL_SERVICE,
]).controller('appengineInstanceDetailsCtrl', AppengineInstanceDetailsController);
