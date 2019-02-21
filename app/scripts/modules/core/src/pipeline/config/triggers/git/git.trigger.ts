import { IController, IScope, module } from 'angular';
import { has, trim } from 'lodash';

import { SETTINGS } from 'core/config/settings';
import { IGitTrigger } from 'core/domain/ITrigger';
import { Registry } from 'core/registry';
import { ServiceAccountReader } from 'core/serviceAccount/ServiceAccountReader';
import { GitTriggerExecutionStatus } from './GitTriggerExecutionStatus';

class GitTriggerController implements IController {
  public fiatEnabled: boolean = SETTINGS.feature.fiatEnabled;
  public serviceAccounts: string[] = [];
  public gitTriggerTypes = SETTINGS.gitSources || ['stash', 'github', 'bitbucket', 'gitlab'];
  public displayText: any = {
    'pipeline.config.git.project': {
      bitbucket: 'Team or User',
      github: 'Organization or User',
      gitlab: 'Organization or User',
      stash: 'Project',
    },
    'pipeline.config.git.slug': {
      bitbucket: 'Repo name',
      github: 'Project',
      gitlab: 'Project',
      stash: 'Repo name',
    },
    'vm.trigger.project': {
      bitbucket: 'Team or User name, i.e. spinnaker for bitbucket.org/spinnaker/echo',
      github: 'Organization or User name, i.e. spinnaker for github.com/spinnaker/echo',
      gitlab: 'Organization or User name, i.e. spinnaker for gitlab.com/spinnaker/echo',
      stash: 'Project name, i.e. SPKR for stash.mycorp.com/projects/SPKR/repos/echo',
    },
    'vm.trigger.slug': {
      bitbucket: 'Repository name (not the url), i.e, echo for bitbucket.org/spinnaker/echo',
      github: 'Project name (not the url), i.e, echo for github.com/spinnaker/echo',
      gitlab: 'Project name (not the url), i.e. echo for gitlab.com/spinnaker/echo',
      stash: 'Repository name (not the url), i.e, echo for stash.mycorp.com/projects/SPKR/repos/echo',
    },
  };

  public static $inject = ['trigger', '$scope'];
  constructor(public trigger: IGitTrigger, private $scope: IScope) {
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
    ServiceAccountReader.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });
    if (this.gitTriggerTypes.length === 1) {
      this.trigger.source = this.gitTriggerTypes[0];
    }
  }
}

export const GIT_TRIGGER = 'spinnaker.core.pipeline.trigger.git';
module(GIT_TRIGGER, [])
  .config(() => {
    Registry.pipeline.registerTrigger({
      label: 'Git',
      description: 'Executes the pipeline on a git push',
      key: 'git',
      controller: 'GitTriggerCtrl',
      controllerAs: 'vm',
      templateUrl: require('./gitTrigger.html'),
      executionStatusComponent: GitTriggerExecutionStatus,
      validators: [
        {
          type: 'serviceAccountAccess',
          message: `You do not have access to the service account configured in this pipeline's git trigger.
                    You will not be able to save your edits to this pipeline.`,
          preventSave: true,
        },
      ],
    });
  })
  .controller('GitTriggerCtrl', GitTriggerController);
