import { copy, IController, module } from 'angular'
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import {
  Application,
  MANIFEST_WRITER,
  ManifestWriter,
  TASK_MONITOR_BUILDER,
  TaskMonitor,
  TaskMonitorBuilder
} from '@spinnaker/core';
import { IManifestCoordinates } from '../IManifestCoordinates';

interface IScaleCommand {
  name: string;
  location: string;
  account: string;
  reason: string;
  replicas: number;
}

class KubernetesManifestScaleController implements IController {
  public taskMonitor: TaskMonitor;
  public command: IScaleCommand;
  public verification = {
    verified: false
  };

  constructor(coordinates: IManifestCoordinates,
              taskMonitorBuilder: TaskMonitorBuilder,
              currentReplicas: number,
              private $uibModalInstance: IModalServiceInstance,
              private manifestWriter: ManifestWriter,
              private application: Application) {
    'ngInject';

    this.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      title: `Scaling ${coordinates.name} in ${coordinates.namespace}`,
      application: application,
      modalInstance: $uibModalInstance,
    });

    this.command = {
      name: coordinates.name,
      location: coordinates.namespace,
      account: coordinates.account,
      reason: null,
      replicas: currentReplicas,
    };
  }

  public isValid(): boolean {
    return this.verification.verified;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  };

  public scale(): void {
    this.taskMonitor.submit(() => {
      const payload = copy(this.command) as any;
      payload.cloudProvider = 'kubernetes';

      return this.manifestWriter.scaleManifest(payload, this.application);
    });
  }
}

export const KUBERNETES_MANIFEST_SCALE_CTRL = 'spinnaker.kubernetes.v2.manifest.scale.controller';

module(KUBERNETES_MANIFEST_SCALE_CTRL, [
  TASK_MONITOR_BUILDER,
  MANIFEST_WRITER,
])
  .controller('kubernetesV2ManifestScaleCtrl', KubernetesManifestScaleController);
