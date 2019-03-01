import { IController, IScope } from 'angular';
import { get, defaults } from 'lodash';
import { dump } from 'js-yaml';
import { ExpectedArtifactSelectorViewController, NgGenericArtifactDelegate } from '@spinnaker/core';
import { IPatchOptions, MergeStrategy } from './patchOptionsForm.component';
import {
  IKubernetesManifestCommandMetadata,
  KubernetesManifestCommandBuilder,
} from 'kubernetes/v2/manifest/manifestCommandBuilder.service';
import { JSON_EDITOR_TAB_SIZE } from 'kubernetes/v2/manifest/editor/json/JsonEditor';

export enum EditorMode {
  json = 'json',
  yaml = 'yaml',
}

export const mergeStrategyToEditorMode: { [m in MergeStrategy]: EditorMode } = {
  [MergeStrategy.strategic]: EditorMode.yaml,
  [MergeStrategy.json]: EditorMode.json,
  [MergeStrategy.merge]: EditorMode.json,
};

export class KubernetesV2PatchManifestConfigCtrl implements IController {
  public state = {
    loaded: false,
  };

  public metadata: IKubernetesManifestCommandMetadata;
  public textSource = 'text';
  public artifactSource = 'artifact';
  public sources = [this.textSource, this.artifactSource];
  public rawPatchBody: string;

  private manifestArtifactDelegate: NgGenericArtifactDelegate;
  private manifestArtifactController: ExpectedArtifactSelectorViewController;

  public static $inject = ['$scope'];
  constructor(private $scope: IScope) {
    const defaultOptions: IPatchOptions = {
      mergeStrategy: MergeStrategy.strategic,
      record: true,
    };

    if (this.$scope.stage.isNew) {
      this.$scope.stage.options = defaultOptions;
    }

    if (!this.$scope.stage.app) {
      this.$scope.stage.app = this.$scope.application.name;
    }

    this.setRawPatchBody(this.getMergeStrategy());

    this.manifestArtifactDelegate = new NgGenericArtifactDelegate($scope, 'manifest');
    this.manifestArtifactController = new ExpectedArtifactSelectorViewController(this.manifestArtifactDelegate);

    KubernetesManifestCommandBuilder.buildNewManifestCommand(
      this.$scope.application,
      this.$scope.stage.patchBody,
      this.$scope.stage.moniker,
    ).then(builtCommand => {
      if (this.$scope.stage.isNew) {
        defaults(this.$scope.stage, {
          account: builtCommand.command.account,
          manifestArtifactId: builtCommand.command.manifestArtifactId,
          manifestArtifactAccount: builtCommand.command.manifestArtifactAccount,
          patchBody: builtCommand.command.manifests || builtCommand.command.manifest,
          source: this.textSource,
          location: '',
          cloudProvider: 'kubernetes',
        });
      }
      this.metadata = builtCommand.metadata;
      this.state.loaded = true;
      this.manifestArtifactDelegate.setAccounts(get(this, ['metadata', 'backingData', 'artifactAccounts']));
      this.manifestArtifactController.updateAccounts(this.manifestArtifactDelegate.getSelectedExpectedArtifact());
    });
  }

  public handlePatchBodyChange = (rawPatchBody: string, patchBody: any): void => {
    this.rawPatchBody = rawPatchBody;
    if (this.getEditorMode() === EditorMode.yaml) {
      // YamlEditor patchBody is list of YAML documents, take first as patch
      this.$scope.stage.patchBody = Array.isArray(patchBody) && patchBody.length > 0 ? patchBody[0] : null;
    } else {
      this.$scope.stage.patchBody = patchBody;
    }
    // Called from a React component.
    this.$scope.$applyAsync();
  };

  public canShowAccountSelect() {
    return (
      !this.$scope.showCreateArtifactForm &&
      (this.manifestArtifactController.accountsForArtifact.length > 1 &&
        this.manifestArtifactDelegate.getSelectedExpectedArtifact() != null)
    );
  }

  public handleManifestSelectorChange = (): void => {
    this.$scope.$applyAsync();
  };

  private getMergeStrategy = (): MergeStrategy => {
    return this.$scope.stage.options.mergeStrategy;
  };

  public getEditorMode = (): EditorMode => {
    return mergeStrategyToEditorMode[this.getMergeStrategy()];
  };

  private setRawPatchBody = (mergeStrategy: MergeStrategy): void => {
    const editorMode = mergeStrategyToEditorMode[mergeStrategy];
    const patchBody = this.$scope.stage.patchBody;
    try {
      if (editorMode === EditorMode.yaml) {
        this.rawPatchBody = patchBody ? dump(patchBody) : null;
      } else {
        this.rawPatchBody = patchBody ? JSON.stringify(patchBody, null, JSON_EDITOR_TAB_SIZE) : null;
      }
    } catch (e) {
      this.rawPatchBody = null;
    }
  };

  public handleMergeStrategyChange = (): void => {
    this.handlePatchBodyChange('', null);
    this.$scope.$applyAsync();
  };
}
