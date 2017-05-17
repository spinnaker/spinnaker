import {module, IScope, IQService} from 'angular';

import {IGOR_SERVICE, IgorService, BuildServiceType} from 'core/ci/igor.service';
import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';
import {SERVICE_ACCOUNT_SERVICE, ServiceAccountService} from 'core/serviceAccount/serviceAccount.service';
import {IBuildTrigger} from 'core/domain/ITrigger';
import {TRAVIS_TRIGGER_OPTIONS_COMPONENT} from './travisTriggerOptions.component';
import {SETTINGS} from 'core/config/settings';

interface IViewState {
  mastersLoaded: boolean;
  mastersRefreshing: boolean;
  jobsLoaded: boolean;
  jobsRefreshing: boolean;
}

export class TravisTrigger {
  public viewState: IViewState;
  public masters: string[];
  public jobs: string[];
  public filterLimit = 100;
  private filterThreshold = 500;
  private fiatEnabled: boolean;
  private serviceAccounts: string[];

  constructor($scope: IScope,
              public trigger: IBuildTrigger,
              private igorService: IgorService,
              serviceAccountService: ServiceAccountService) {
    'ngInject';
    this.fiatEnabled = SETTINGS.feature.fiatEnabled;
    serviceAccountService.getServiceAccounts().then(accounts => {
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
  };

  public refreshJobs(): void {
    this.viewState.jobsRefreshing = true;
    this.updateJobsList();
  };

  public shouldFilter(): boolean {
    return this.jobs && this.jobs.length >= this.filterThreshold;
  }

  private initializeMasters(): void {
    this.igorService.listMasters(BuildServiceType.Travis).then((masters: string[]) => {
      this.masters = masters;
      this.viewState.mastersLoaded = true;
      this.viewState.mastersRefreshing = false;
    });
  }

  private updateJobsList(): void {
    if (this.trigger && this.trigger.master) {
      this.viewState.jobsLoaded = false;
      this.jobs = [];
      this.igorService.listJobsForMaster(this.trigger.master).then(jobs => {
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
module(TRAVIS_TRIGGER, [
  TRAVIS_TRIGGER_OPTIONS_COMPONENT,
  require('../trigger.directive.js'),
  IGOR_SERVICE,
  SERVICE_ACCOUNT_SERVICE,
  PIPELINE_CONFIG_PROVIDER,
]).config((pipelineConfigProvider: any) => {
  pipelineConfigProvider.registerTrigger({
    label: 'Travis',
    description: 'Listens to a Travis job',
    key: 'travis',
    controller: 'TravisTriggerCtrl',
    controllerAs: '$ctrl',
    templateUrl: require('./travisTrigger.html'),
    manualExecutionHandler: 'travisTriggerExecutionHandler',
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
      }
    ],
  });
}).factory('travisTriggerExecutionHandler', ($q: IQService) => {
  // must provide two fields:
  //   formatLabel (promise): used to supply the label for selecting a trigger when there are multiple triggers
  //   selectorTemplate: provides the HTML to show extra fields
  return {
    formatLabel: (trigger: IBuildTrigger) => {
      return $q.when(`(Travis) ${trigger.master}: ${trigger.job}`);
    },
    selectorTemplate: require('./selectorTemplate.html'),
  };
}).controller('TravisTriggerCtrl', TravisTrigger);
