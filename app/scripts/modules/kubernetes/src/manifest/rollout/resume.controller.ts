import { copy, IController, module } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { Application, ManifestWriter, TaskMonitor } from '@spinnaker/core';
import { IManifestCoordinates } from '../IManifestCoordinates';

interface IResumeRolloutCommand {
  manifestName: string;
  location: string;
  account: string;
  reason: string;
}

class KubernetesManifestResumeRolloutController implements IController {
  public taskMonitor: TaskMonitor;
  public command: IResumeRolloutCommand;
  public verification = {
    verified: false,
  };

  public static $inject = ['coordinates', '$uibModalInstance', 'application'];
  constructor(
    coordinates: IManifestCoordinates,
    private $uibModalInstance: IModalServiceInstance,
    private application: Application,
  ) {
    this.taskMonitor = new TaskMonitor({
      title: `Resume rollout of ${coordinates.name} in ${coordinates.namespace}`,
      application,
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
  }

  public resumeRollout(): void {
    this.taskMonitor.submit(() => {
      const payload = copy(this.command) as any;
      payload.cloudProvider = 'kubernetes';

      return ManifestWriter.resumeRolloutManifest(payload, this.application);
    });
  }
}

export const KUBERNETES_MANIFEST_RESUME_ROLLOUT_CTRL = 'spinnaker.kubernetes.v2.manifest.resumeRollout.controller';

module(KUBERNETES_MANIFEST_RESUME_ROLLOUT_CTRL, []).controller(
  'kubernetesV2ManifestResumeRolloutCtrl',
  KubernetesManifestResumeRolloutController,
);
