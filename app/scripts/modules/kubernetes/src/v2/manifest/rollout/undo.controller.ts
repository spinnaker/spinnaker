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

interface IUndoRolloutCommand {
  manifestName: string;
  location: string;
  account: string;
  reason: string;
  revision: number;
}

interface IRolloutRevision {
  name: string;
  revision: number;
}

class KubernetesManifestUndoRolloutController implements IController {
  public taskMonitor: TaskMonitor;
  public command: IUndoRolloutCommand;
  public verification = {
    verified: false
  };

  constructor(coordinates: IManifestCoordinates,
              taskMonitorBuilder: TaskMonitorBuilder,
              public revisions: IRolloutRevision[],
              private $uibModalInstance: IModalServiceInstance,
              private manifestWriter: ManifestWriter,
              private application: Application) {
    'ngInject';

    this.taskMonitor = taskMonitorBuilder.buildTaskMonitor({
      title: `Undo rollout of ${coordinates.name} in ${coordinates.namespace}`,
      application: application,
      modalInstance: $uibModalInstance,
    });

    this.command = {
      manifestName: coordinates.name,
      location: coordinates.namespace,
      account: coordinates.account,
      reason: null,
      revision: null,
    };
  }

  public isValid(): boolean {
    return this.verification.verified;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  };

  public undoRollout(): void {
    this.taskMonitor.submit(() => {
      const payload = copy(this.command) as any;
      payload.cloudProvider = 'kubernetes';

      return this.manifestWriter.undoRolloutManifest(payload, this.application);
    });
  }
}

export const KUBERNETES_MANIFEST_UNDO_ROLLOUT_CTRL = 'spinnaker.kubernetes.v2.manifest.undoRollout.controller';

module(KUBERNETES_MANIFEST_UNDO_ROLLOUT_CTRL, [
  TASK_MONITOR_BUILDER,
  MANIFEST_WRITER,
])
  .controller('kubernetesV2ManifestUndoRolloutCtrl', KubernetesManifestUndoRolloutController);
