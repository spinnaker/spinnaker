import { module } from 'angular';

import { AccountService } from 'core/account/AccountService';
import { ArtifactTypePatterns } from 'core/artifact';
import { IArtifact } from 'core/domain/IArtifact';
import { Registry } from 'core/registry';

import { ArtifactService } from '../ArtifactService';

export const HELM_ARTIFACT = 'spinnaker.core.pipeline.trigger.artifact.helm';

module(HELM_ARTIFACT, []).config(() => {
  Registry.pipeline.mergeArtifactKind({
    label: 'Helm',
    typePattern: ArtifactTypePatterns.HELM_CHART,
    type: 'helm/chart',
    isDefault: true,
    isMatch: true,
    description: 'A helm chart to be deployed',
    key: 'helm',
    controller: function(artifact: IArtifact) {
      this.artifact = artifact;
      this.artifact.type = 'helm/chart';

      this.onAccountChange = () => {
        this.artifact.artifactAccount = this.selectedArtifactAccount;
        this.artifact.reference = this.selectedArtifactAccount;
        ArtifactService.getArtifactNames('helm/chart', this.artifact.artifactAccount).then(names => {
          this.chartNames = names;
        });
        this.chartVersions = [];
      };
      this.onNameChange = () => {
        ArtifactService.getArtifactVersions('helm/chart', this.artifact.artifactAccount, this.artifact.name).then(
          versions => {
            this.chartVersions = versions;
          },
        );
      };
      AccountService.getArtifactAccounts().then(accounts => {
        this.artifactAccounts = accounts
          .filter(account => account.types.includes('helm/chart'))
          .map(account => account.name);
        if (artifact.artifactAccount) {
          this.selectedArtifactAccount = accounts.filter(
            account => account.types.includes('helm/chart') && account.name === this.artifact.artifactAccount,
          )[0].name;
        }
      });

      if (artifact.artifactAccount) {
        ArtifactService.getArtifactNames('helm/chart', this.artifact.artifactAccount).then(names => {
          this.chartNames = names;
        });
        if (artifact.name) {
          ArtifactService.getArtifactVersions('helm/chart', this.artifact.artifactAccount, this.artifact.name).then(
            versions => {
              this.chartVersions = versions;
            },
          );
        }
      }
    },
    controllerAs: 'ctrl',
    template: `
<div class="col-md-12">
 <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Account
      <help-field key="pipeline.config.expectedArtifact.helm.account"></help-field>
    </label>
    <div class="col-md-8">
      <select class="form-control input-sm" ng-model="ctrl.selectedArtifactAccount"
                                            ng-options="account for account in ctrl.artifactAccounts"
                                            ng-change="ctrl.onAccountChange()">
      </select>
    </div>
  </div>
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Name
      <help-field key="pipeline.config.expectedArtifact.helm.name"></help-field>
    </label>
    <div class="col-md-8">
     <select class="form-control input-sm" ng-model="ctrl.artifact.name"
                                           ng-options="name for name in ctrl.chartNames"
                                           ng-change="ctrl.onNameChange()">
     </select>
    </div>
 </div>
  <div class="form-group row">
    <label class="col-md-2 sm-label-right">
      Version
      <help-field key="pipeline.config.expectedArtifact.helm.version"></help-field>
    </label>
    <div class="col-md-8">
      <select class="form-control input-sm" ng-model="ctrl.artifact.version"
                                            ng-options="version for version in ctrl.chartVersions">
      </select>
    </div>
  </div>
</div>
`,
  });
});
