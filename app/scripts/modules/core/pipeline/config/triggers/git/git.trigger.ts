import {IScope, module} from 'angular';
import {has, trim} from 'lodash';

import {SETTINGS} from 'core/config/settings';
import {IGitTrigger} from 'core/domain/ITrigger';
import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';
import {SERVICE_ACCOUNT_SERVICE, ServiceAccountService} from 'core/serviceAccount/serviceAccount.service';

class GitTriggerController {

  public fiatEnabled: boolean = SETTINGS.feature.fiatEnabled;
  public serviceAccounts: string[] = [];
  public gitTriggerTypes = SETTINGS.gitSources || ['stash', 'github', 'bitbucket'];
  public displayText: any = {
    'pipeline.config.git.project': {
      'bitbucket': 'Team or User',
      'github': 'Organization or User',
      'stash': 'Project',
    },
    'pipeline.config.git.slug': {
      'bitbucket': 'Repo name',
      'github': 'Project',
      'stash': 'Repo name'
    },
    'vm.trigger.project': {
      'bitbucket': 'Team or User name, i.e. spinnaker for bitbucket.org/spinnaker/echo',
      'github': 'Organization or User name, i.e. spinnaker for github.com/spinnaker/echo',
      'stash': 'Project name, i.e. SPKR for stash.mycorp.com/projects/SPKR/repos/echo'
    },
    'vm.trigger.slug': {
      'bitbucket': 'Repository name (not the url), i.e, echo for bitbucket.org/spinnaker/echo',
      'github': 'Project name (not the url), i.e, echo for github.com/spinnaker/echo',
      'stash': 'Repository name (not the url), i.e, echo for stash.mycorp.com/projects/SPKR/repos/echo'
    }
  };

  static get $inject() { return ['trigger', '$scope', 'serviceAccountService']; }
  constructor(public trigger: IGitTrigger, private $scope: IScope, private serviceAccountService: ServiceAccountService) {
    this.initialize();
  }

  public updateBranch(): void {
    if (trim(this.trigger.branch) === '') {
      this.trigger.branch = null;
    }
  }

  public initialize() {
    if (has(this.$scope.application, 'attributes.repoProjectKey') && !this.trigger.source) {
      const attributes: any = this.$scope.application.attributes;
      this.trigger.source = attributes.repoType;
      this.trigger.project = attributes.repoProjectKey;
      this.trigger.slug = attributes.repoSlug;
    }
    this.serviceAccountService.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });
    if (this.gitTriggerTypes.length === 1) {
      this.trigger.source = this.gitTriggerTypes[0];
    }
  }
}

export const GIT_TRIGGER = 'spinnaker.core.pipeline.trigger.git';
module(GIT_TRIGGER, [
  SERVICE_ACCOUNT_SERVICE,
  PIPELINE_CONFIG_PROVIDER,
]).config((pipelineConfigProvider: any) => {
  pipelineConfigProvider.registerTrigger({
    label: 'Git',
    description: 'Executes the pipeline on a git push',
    key: 'git',
    controller: 'GitTriggerCtrl',
    controllerAs: 'vm',
    templateUrl: require('./gitTrigger.html'),
    validators: [
      {
        type: 'serviceAccountAccess',
        message: `You do not have access to the service account configured in this pipeline's git trigger.
                    You will not be able to save your edits to this pipeline.`,
        preventSave: true,
      }
    ]
  });
}).controller('GitTriggerCtrl', GitTriggerController);
