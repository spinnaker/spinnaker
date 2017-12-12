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

interface IPauseRolloutCommand {
  manifestName: string;
  location: string;
  account: string;
  reason: string;
}

class KubernetesManifestPauseRolloutController implements IController {
  public taskMonitor: TaskMonitor;
  public command: IPauseRolloutCommand;
  public verification = {
    verified: false
  };

  constructor(coordinates: IManifestCoordinates,
              taskMonitorBuilder: TaskMonitorBuilder,
              private $uibModalInstance: IModalServiceInstance,
              private manifestWriter: ManifestWriter,
              private application: Application) {
    'ngInject';

    this.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      title: `Pause rollout of ${coordinates.name} in ${coordinates.namespace}`,
      application: application,
      modalInstance: $uibModalInstance,
    });

    this.command = {
      manifestName: coordinates.name,
      location: coordinates.namespace,
      account: coordinates.account,
      reason: null,
    };
  }

  public isValid(): boolean {
    return this.verification.verified;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  };

  public pauseRollout(): void {
    this.taskMonitor.submit(() => {
      const payload = copy(this.command) as any;
      payload.cloudProvider = 'kubernetes';

      return this.manifestWriter.pauseRolloutManifest(payload, this.application);
    });
  }
}

export const KUBERNETES_MANIFEST_PAUSE_ROLLOUT_CTRL = 'spinnaker.kubernetes.v2.manifest.pauseRollout.controller';

module(KUBERNETES_MANIFEST_PAUSE_ROLLOUT_CTRL, [
  TASK_MONITOR_BUILDER,
  MANIFEST_WRITER,
])
  .controller('kubernetesV2ManifestPauseRolloutCtrl', KubernetesManifestPauseRolloutController);
