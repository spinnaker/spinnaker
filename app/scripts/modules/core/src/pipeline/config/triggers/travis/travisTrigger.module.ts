import { IController, IScope, module } from 'angular';

import { IgorService, BuildServiceType } from 'core/ci/igor.service';
import { Registry } from 'core/registry';
import { ServiceAccountReader } from 'core/serviceAccount/ServiceAccountReader';
import { IBuildTrigger } from 'core/domain/ITrigger';
import { SETTINGS } from 'core/config/settings';

import { TravisTriggerTemplate } from './TravisTriggerTemplate';

export interface ITravisTriggerViewState {
  mastersLoaded: boolean;
  mastersRefreshing: boolean;
  jobsLoaded: boolean;
  jobsRefreshing: boolean;
}

export class TravisTrigger implements IController {
  public viewState: ITravisTriggerViewState;
  public masters: string[];
  public jobs: string[];
  public filterLimit = 100;
  private filterThreshold = 500;
  public fiatEnabled: boolean;
  public serviceAccounts: string[];

  constructor($scope: IScope, public trigger: IBuildTrigger) {
    'ngInject';
    this.fiatEnabled = SETTINGS.feature.fiatEnabled;
    ServiceAccountReader.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });
    this.viewState = {
      mastersLoaded: false,
      mastersRefreshing: false,
      jobsLoaded: false,
      jobsRefreshing: false,
    };
    this.initializeMasters();
    $scope.$watch(() => trigger.master, () => this.updateJobsList());
  }

  public refreshMasters(): void {
    this.viewState.mastersRefreshing = true;
    this.initializeMasters();
  }

  public refreshJobs(): void {
    this.viewState.jobsRefreshing = true;
    this.updateJobsList();
  }

  public shouldFilter(): boolean {
    return this.jobs && this.jobs.length >= this.filterThreshold;
  }

  private initializeMasters(): void {
    IgorService.listMasters(BuildServiceType.Travis).then((masters: string[]) => {
      this.masters = masters;
      this.viewState.mastersLoaded = true;
      this.viewState.mastersRefreshing = false;
    });
  }

  private updateJobsList(): void {
    if (this.trigger && this.trigger.master) {
      this.viewState.jobsLoaded = false;
      this.jobs = [];
      IgorService.listJobsForMaster(this.trigger.master).then(jobs => {
        this.viewState.jobsLoaded = true;
        this.viewState.jobsRefreshing = false;
        this.jobs = jobs;
        if (jobs.length && !this.jobs.includes(this.trigger.job)) {
          this.trigger.job = '';
        }
      });
    }
  }
}

export const TRAVIS_TRIGGER = 'spinnaker.core.pipeline.config.trigger.travis';
module(TRAVIS_TRIGGER, [require('../trigger.directive.js').name])
  .config(() => {
    Registry.pipeline.registerTrigger({
      label: 'Travis',
      description: 'Listens to a Travis job',
      key: 'travis',
      controller: 'TravisTriggerCtrl',
      controllerAs: '$ctrl',
      templateUrl: require('./travisTrigger.html'),
      manualExecutionComponent: TravisTriggerTemplate,
      providesVersionForBake: true,
      validators: [
        {
          type: 'requiredField',
          fieldName: 'job',
          message: '<strong>Job</strong> is a required field on Travis triggers.',
        },
        {
          type: 'serviceAccountAccess',
          message: `You do not have access to the service account configured in this pipeline's Travis trigger.
                    You will not be able to save your edits to this pipeline.`,
          preventSave: true,
        },
      ],
    });
  })
  .controller('TravisTriggerCtrl', TravisTrigger);
