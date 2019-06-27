import { IController, IScope } from 'angular';
import { $q } from 'ngimport';
import {
  ExpectedArtifactSelectorViewController,
  NgGenericArtifactDelegate,
  AccountService,
  IAccountDetails,
} from '@spinnaker/core';

export class DeployCloudFormationStackConfigController implements IController {
  public state = {
    loaded: false,
  };

  public textSource = 'text';
  public artifactSource = 'artifact';
  public sources = [this.textSource, this.artifactSource];
  public showClusterSelect = false;
  public accounts: IAccountDetails[];

  public cloudFormationStackArtifactDelegate: NgGenericArtifactDelegate;
  public cloudFormationStackArtifactController: ExpectedArtifactSelectorViewController;

  constructor(private $scope: IScope) {
    const dataToFetch = {
      accounts: AccountService.getAllAccountDetailsForProvider('aws'),
      artifactAccounts: AccountService.getArtifactAccounts(),
    };
    $q.all(dataToFetch).then(backingData => {
      const { accounts, artifactAccounts } = backingData;
      this.accounts = accounts;
      this.state.loaded = true;
      this.cloudFormationStackArtifactDelegate.setAccounts(artifactAccounts);
      this.cloudFormationStackArtifactController.updateAccounts(
        this.cloudFormationStackArtifactDelegate.getSelectedExpectedArtifact(),
      );
    });
    this.cloudFormationStackArtifactDelegate = new NgGenericArtifactDelegate($scope, 'stack');
    this.cloudFormationStackArtifactController = new ExpectedArtifactSelectorViewController(
      this.cloudFormationStackArtifactDelegate,
    );
  }

  public canShowAccountSelect() {
    return (
      this.$scope.stage.source === this.artifactSource &&
      !this.$scope.showCreateArtifactForm &&
      this.cloudFormationStackArtifactController.accountsForArtifact.length > 1
    );
  }

  public isChangeSet() {
    return this.$scope.stage.isChangeSet;
  }

  public toggleChangeSet() {
    this.$scope.stage.isChangeSet = !this.$scope.stage.isChangeSet;
  }
}
