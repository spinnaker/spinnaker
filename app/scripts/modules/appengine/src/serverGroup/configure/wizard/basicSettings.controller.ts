import { StateService } from '@uirouter/angularjs';
import { extend, IController, IControllerService, IScope, module } from 'angular';
import { set } from 'lodash';

import {
  ArtifactTypePatterns,
  excludeAllTypesExcept,
  ExpectedArtifactSelectorViewController,
  IArtifact,
  IExpectedArtifact,
  NgAppEngineDeployArtifactDelegate,
} from '@spinnaker/core';
import { GitCredentialType, IAppengineAccount } from '../../../domain/index';

import { AppengineSourceType, IAppengineServerGroupCommand } from '../serverGroupCommandBuilder.service';

interface IAppengineBasicSettingsScope extends IScope {
  command: IAppengineServerGroupCommand;
}

class AppengineServerGroupBasicSettingsCtrl implements IController {
  public static $inject = ['$scope', '$state', '$controller', '$uibModalStack'];

  constructor(
    public $scope: IAppengineBasicSettingsScope,
    $state: StateService,
    $controller: IControllerService,
    $uibModalStack: any,
  ) {
    extend(
      this,
      $controller('BasicSettingsMixin', {
        $scope,
        imageReader: null,
        $uibModalStack,
        $state,
      }),
    );

    if (!this.$scope.command.gitCredentialType) {
      this.onAccountChange();
    }

    this.$scope.containerArtifactDelegate = new NgAppEngineDeployArtifactDelegate($scope, [
      ArtifactTypePatterns.DOCKER_IMAGE,
    ]);
    this.$scope.containerArtifactController = new ExpectedArtifactSelectorViewController(
      this.$scope.containerArtifactDelegate,
    );
    this.$scope.gcsArtifactDelegate = new NgAppEngineDeployArtifactDelegate($scope, [ArtifactTypePatterns.GCS_OBJECT]);
    this.$scope.gcsArtifactController = new ExpectedArtifactSelectorViewController(this.$scope.gcsArtifactDelegate);
  }

  public isGitSource(): boolean {
    return this.$scope.command.sourceType === AppengineSourceType.GIT;
  }

  public isGcsSource(): boolean {
    return this.$scope.command.sourceType === AppengineSourceType.GCS;
  }

  public isContainerImageSource(): boolean {
    return this.$scope.command.sourceType === AppengineSourceType.CONTAINER_IMAGE;
  }

  public toggleResolveViaTrigger(): void {
    this.$scope.command.fromTrigger = !this.$scope.command.fromTrigger;
    delete this.$scope.command.trigger;
    delete this.$scope.command.branch;
  }

  public onTriggerChange(): void {
    set(this, '$scope.command.trigger.matchBranchOnRegex', undefined);
  }

  public onAccountChange(): void {
    const account = this.findAccountInBackingData();
    if (account) {
      this.$scope.command.gitCredentialType = this.getSupportedGitCredentialTypes()[0];
      this.$scope.command.region = account.region;
    } else {
      this.$scope.command.gitCredentialType = 'NONE';
      delete this.$scope.command.region;
    }
  }

  public getSupportedGitCredentialTypes(): GitCredentialType[] {
    const account = this.findAccountInBackingData();
    if (account && account.supportedGitCredentialTypes) {
      return account.supportedGitCredentialTypes;
    } else {
      return ['NONE'];
    }
  }

  public humanReadableGitCredentialType(type: GitCredentialType): string {
    switch (type) {
      case 'HTTPS_USERNAME_PASSWORD':
        return 'HTTPS with username and password';
      case 'HTTPS_GITHUB_OAUTH_TOKEN':
        return 'HTTPS with Github OAuth token';
      case 'SSH':
        return 'SSH';
      case 'NONE':
        return 'No credentials';
      default:
        return 'No credentials';
    }
  }

  public readonly excludedGcsArtifactTypes = excludeAllTypesExcept(ArtifactTypePatterns.GCS_OBJECT);
  public readonly excludedContainerArtifactTypes = excludeAllTypesExcept(ArtifactTypePatterns.DOCKER_IMAGE);

  public onExpectedArtifactEdited = (artifact: IArtifact): void => {
    this.$scope.$applyAsync(() => {
      this.$scope.command.expectedArtifactId = null;
      this.$scope.command.expectedArtifact = artifact;
    });
  };

  public onExpectedArtifactSelected = (expectedArtifact: IExpectedArtifact): void => {
    this.onChangeExpectedArtifactId(expectedArtifact.id);
  };

  public onChangeExpectedArtifactId = (artifactId: string): void => {
    this.$scope.$applyAsync(() => {
      this.$scope.command.expectedArtifactId = artifactId;
      this.$scope.command.expectedArtifact = null;
    });
  };

  public onExpectedArtifactAccountSelected = (accountName: string): void => {
    this.$scope.$applyAsync(() => {
      this.$scope.command.storageAccountName = accountName;
    });
  };

  private findAccountInBackingData(): IAppengineAccount {
    return this.$scope.command.backingData.accounts.find((account: IAppengineAccount) => {
      return this.$scope.command.credentials === account.name;
    });
  }
}

export const APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL = 'spinnaker.appengine.basicSettings.controller';

module(APPENGINE_SERVER_GROUP_BASIC_SETTINGS_CTRL, []).controller(
  'appengineServerGroupBasicSettingsCtrl',
  AppengineServerGroupBasicSettingsCtrl,
);
