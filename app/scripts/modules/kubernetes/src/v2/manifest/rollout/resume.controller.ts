import { copy, IController, module } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { Application, MANIFEST_WRITER, ManifestWriter, TaskMonitor } from '@spinnaker/core';
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

  constructor(
    coordinates: IManifestCoordinates,
    private $uibModalInstance: IModalServiceInstance,
    private manifestWriter: ManifestWriter,
    private application: Application,
  ) {
    'ngInject';

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

      return this.manifestWriter.resumeRolloutManifest(payload, this.application);
    });
  }
}

export const KUBERNETES_MANIFEST_RESUME_ROLLOUT_CTRL = 'spinnaker.kubernetes.v2.manifest.resumeRollout.controller';

module(KUBERNETES_MANIFEST_RESUME_ROLLOUT_CTRL, [MANIFEST_WRITER]).controller(
  'kubernetesV2ManifestResumeRolloutCtrl',
  KubernetesManifestResumeRolloutController,
);
