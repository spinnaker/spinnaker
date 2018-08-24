import { IController, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';

import { Application, SERVER_GROUP_WRITER, TaskMonitor, ManifestWriter } from '@spinnaker/core';

import {
  IKubernetesManifestCommand,
  IKubernetesManifestCommandMetadata,
  KubernetesManifestCommandBuilder,
} from 'kubernetes/v2/manifest/manifestCommandBuilder.service';

class KubernetesManifestWizardCtrl implements IController {
  public state = {
    loaded: false,
    isNew: true,
  };
  public taskMonitor: TaskMonitor;
  public command: IKubernetesManifestCommand;
  public metadata: IKubernetesManifestCommandMetadata;

  constructor(private $uibModalInstance: IModalInstanceService, private application: Application) {
    'ngInject';
    KubernetesManifestCommandBuilder.buildNewManifestCommand(application).then(builtCommand => {
      const { command, metadata } = builtCommand;
      this.command = command;
      this.metadata = metadata;

      this.initialize();
      this.state.loaded = true;
    });
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public submit(): void {
    const command = KubernetesManifestCommandBuilder.copyAndCleanCommand(this.command);
    const submitMethod = () => ManifestWriter.deployManifest(command, this.application);
    this.taskMonitor.submit(submitMethod);
  }

  private initialize(): void {
    this.taskMonitor = new TaskMonitor({
      application: this.application,
      title: 'Deploying your manifest',
      modalInstance: this.$uibModalInstance,
    });
  }

  public showSubmitButton(): boolean {
    return true;
  }

  public isValid(): boolean {
    return KubernetesManifestCommandBuilder.manifestCommandIsValid(this.command);
  }
}

export const KUBERNETES_MANIFEST_CTRL = 'spinnaker.kubernetes.v2.manifest.wizard.controller';
module(KUBERNETES_MANIFEST_CTRL, [SERVER_GROUP_WRITER]).controller(
  'kubernetesManifestWizardCtrl',
  KubernetesManifestWizardCtrl,
);
