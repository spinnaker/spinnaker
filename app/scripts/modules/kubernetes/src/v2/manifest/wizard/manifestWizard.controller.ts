import { IController, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';

import {
  Application,
  SERVER_GROUP_WRITER,
  TASK_MONITOR_BUILDER,
  TaskMonitor,
  TaskMonitorBuilder,
  ManifestWriter
} from '@spinnaker/core';

import {
  IKubernetesManifestCommand, IKubernetesManifestCommandMetadata,
  KUBERNETES_MANIFEST_COMMAND_BUILDER,
  KubernetesManifestCommandBuilder
} from '../manifestCommandBuilder.service';

class KubernetesManifestWizardCtrl implements IController {
  public state = {
    loaded: false,
    isNew: true,
  };
  public taskMonitor: TaskMonitor;
  public command: IKubernetesManifestCommand;
  public metadata: IKubernetesManifestCommandMetadata;

  constructor(private $uibModalInstance: IModalInstanceService,
              private application: Application,
              private manifestWriter: ManifestWriter,
              private taskMonitorBuilder: TaskMonitorBuilder,
              private kubernetesManifestCommandBuilder: KubernetesManifestCommandBuilder) {
    'ngInject';
    this.kubernetesManifestCommandBuilder.buildNewManifestCommand(application)
      .then((builtCommand) => {
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
    const command = this.kubernetesManifestCommandBuilder.copyAndCleanCommand(this.metadata, this.command);
    const submitMethod = () => this.manifestWriter.deployManifest(command, this.application);
    this.taskMonitor.submit(submitMethod);
  }

  private initialize(): void {
    this.taskMonitor = this.taskMonitorBuilder.buildTaskMonitor({
      application: this.application,
      title: 'Deploying your manifest',
      modalInstance: this.$uibModalInstance,
    });
  }

  public showSubmitButton(): boolean {
    return true;
  }

  public isValid(): boolean {
    return this.kubernetesManifestCommandBuilder.manifestCommandIsValid(this.command);
  }
}

export const KUBERNETES_MANIFEST_CTRL = 'spinnaker.kubernetes.v2.manifest.wizard.controller';
module(KUBERNETES_MANIFEST_CTRL, [
  SERVER_GROUP_WRITER,
  TASK_MONITOR_BUILDER,
  KUBERNETES_MANIFEST_COMMAND_BUILDER,
]).controller('kubernetesManifestWizardCtrl', KubernetesManifestWizardCtrl);
