import { copy, IController, module } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { Application, ManifestWriter, TaskMonitor } from '@spinnaker/core';
import { IManifestCoordinates } from '../IManifestCoordinates';

interface IUndoRolloutCommand {
  manifestName: string;
  location: string;
  account: string;
  reason: string;
  revision: number;
}

interface IRolloutRevision {
  label: string;
  revision: number;
}

class KubernetesManifestUndoRolloutController implements IController {
  public taskMonitor: TaskMonitor;
  public command: IUndoRolloutCommand;
  public verification = {
    verified: false,
  };

  public static $inject = ['coordinates', 'revisions', '$uibModalInstance', 'application'];
  constructor(
    coordinates: IManifestCoordinates,
    public revisions: IRolloutRevision[],
    private $uibModalInstance: IModalServiceInstance,
    private application: Application,
  ) {
    this.taskMonitor = new TaskMonitor({
      title: `Undo rollout of ${coordinates.name} in ${coordinates.namespace}`,
      application,
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
  }

  public undoRollout(): void {
    this.taskMonitor.submit(() => {
      const payload = copy(this.command) as any;
      payload.cloudProvider = 'kubernetes';

      return ManifestWriter.undoRolloutManifest(payload, this.application);
    });
  }
}

export const KUBERNETES_MANIFEST_UNDO_ROLLOUT_CTRL = 'spinnaker.kubernetes.v2.manifest.undoRollout.controller';

module(KUBERNETES_MANIFEST_UNDO_ROLLOUT_CTRL, []).controller(
  'kubernetesV2ManifestUndoRolloutCtrl',
  KubernetesManifestUndoRolloutController,
);
