import { copy, IController, module } from 'angular';
import { IModalServiceInstance } from 'angular-ui-bootstrap';

import { Application, ManifestWriter, TaskMonitor } from '@spinnaker/core';
import { IManifestCoordinates } from '../IManifestCoordinates';
import { KUBERNETES_DELETE_MANIFEST_OPTIONS_FORM } from './deleteOptionsForm.component';

export interface IDeleteCommand {
  manifestName: string;
  location: string;
  account: string;
  reason: string;
  options: IDeleteOptions;
}

export interface IDeleteOptions {
  gracePeriodSeconds?: number;
  cascading: boolean;
}

class KubernetesManifestDeleteController implements IController {
  public taskMonitor: TaskMonitor;
  public command: IDeleteCommand;
  public verification = {
    verified: false,
  };

  public static $inject = ['coordinates', '$uibModalInstance', 'application', 'manifestController'];
  constructor(
    coordinates: IManifestCoordinates,
    private $uibModalInstance: IModalServiceInstance,
    private application: Application,
    public manifestController: string,
  ) {
    this.taskMonitor = new TaskMonitor({
      title: `Deleting ${coordinates.name} in ${coordinates.namespace}`,
      application,
      modalInstance: $uibModalInstance,
    });

    this.command = {
      manifestName: coordinates.name,
      location: coordinates.namespace,
      account: coordinates.account,
      reason: null,
      options: {
        cascading: true,
      },
    };
  }

  public isValid(): boolean {
    return this.verification.verified;
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public delete(): void {
    this.taskMonitor.submit(() => {
      const payload = copy(this.command) as any;
      payload.cloudProvider = 'kubernetes';
      // cascading is the kubectl term (more familiar) orphanDependants is the k8s server term
      payload.options.orphanDependants = !payload.options.cascading;
      delete payload.options.cascading;

      return ManifestWriter.deleteManifest(payload, this.application);
    });
  }
}

export const KUBERNETES_MANIFEST_DELETE_CTRL = 'spinnaker.kubernetes.v2.manifest.delete.controller';

module(KUBERNETES_MANIFEST_DELETE_CTRL, [KUBERNETES_DELETE_MANIFEST_OPTIONS_FORM]).controller(
  'kubernetesV2ManifestDeleteCtrl',
  KubernetesManifestDeleteController,
);
