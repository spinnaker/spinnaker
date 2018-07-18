import { IController, IScope, module } from 'angular';

import { IgorService, BuildServiceType } from 'core/ci/igor.service';
import { Registry } from 'core/registry';
import { ServiceAccountReader } from 'core/serviceAccount/ServiceAccountReader';
import { IWerckerTrigger } from 'core/domain/ITrigger';
import { SETTINGS } from 'core/config/settings';

import { WerckerTriggerTemplate } from './WerckerTriggerTemplate';

export interface IWerckerTriggerViewState {
  mastersLoaded: boolean;
  mastersRefreshing: boolean;
  appsLoaded: boolean;
  appsRefreshing: boolean;
}

export class WerckerTrigger implements IController {
  public viewState: IWerckerTriggerViewState;
  public masters: string[];
  public apps: string[];
  public pipelines: string[];
  public jobs: string[];
  public app: string;
  public pipeline: string;
  public job: string;
  public filterLimit = 100;
  private filterThreshold = 500;
  public fiatEnabled: boolean;
  public serviceAccounts: string[];

  constructor($scope: IScope, public trigger: IWerckerTrigger) {
    'ngInject';
    this.fiatEnabled = SETTINGS.feature.fiatEnabled;
    ServiceAccountReader.getServiceAccounts().then(accounts => {
      this.serviceAccounts = accounts || [];
    });
    this.viewState = {
      mastersLoaded: false,
      mastersRefreshing: false,
      appsLoaded: false,
      appsRefreshing: false,
    };
    this.initializeMasters();
    this.updateJob();

    $scope.$watch(() => trigger.master, () => this.updateAppsList());
    $scope.$watch(() => trigger.app, () => this.updateJobsList());
    $scope.$watch(() => trigger.pipeline, () => this.updateJob());
  }

  public refreshMasters(): void {
    this.viewState.mastersRefreshing = true;
    this.initializeMasters();
  }

  public shouldFilter(): boolean {
    return this.jobs && this.jobs.length >= this.filterThreshold;
  }

  private initializeMasters(): void {
    IgorService.listMasters(BuildServiceType.Wercker).then((masters: string[]) => {
      this.masters = masters;
      this.viewState.mastersLoaded = true;
      this.viewState.mastersRefreshing = false;
    });
  }

  public refreshApps(): void {
    this.viewState.appsRefreshing = true;
    this.updateAppsList();
  }

  private updateAppsList(): void {
    if (this.trigger && this.trigger.master) {
      this.viewState.appsLoaded = false;
      this.apps = [];
      IgorService.listJobsForMaster(this.trigger.master).then(jobs => {
        this.viewState.appsLoaded = true;
        this.viewState.appsRefreshing = false;
        const apps = Object.create({});
        jobs.forEach(function(app) {
          const orgApp = app.substring(app.indexOf('/') + 1, app.lastIndexOf('/'));
          apps[orgApp] = orgApp;
        });
        this.apps = Object.keys(apps);
        this.jobs = jobs;
        if (this.apps.length && !this.apps.includes(this.trigger.app)) {
          this.trigger.app = '';
          this.trigger.pipeline = '';
          this.trigger.job = '';
        }
      });
    }
  }

  public refreshJobs(): void {
    this.viewState.appsRefreshing = true;
    this.updateJobsList();
  }

  private updateJobsList(): void {
    if (this.trigger && this.trigger.app) {
      this.pipelines = [];
      this.viewState.appsLoaded = true;
      this.viewState.appsRefreshing = false;
      if (this.jobs) {
        const pipelines = Object.create({});
        const appSelected = this.trigger.app;
        this.jobs.forEach(function(app) {
          if (appSelected === app.substring(app.indexOf('/') + 1, app.lastIndexOf('/'))) {
            const pl = app.substring(app.lastIndexOf('/') + 1);
            pipelines[pl] = pl;
          }
        });
        this.pipelines = Object.keys(pipelines);
      }
      if (this.pipelines.length && !this.pipelines.includes(this.trigger.pipeline)) {
        this.trigger.pipeline = '';
        this.trigger.job = '';
      }
    }
  }

  private updateJob(): void {
    if (this.trigger && this.trigger.app && this.trigger.pipeline) {
      this.trigger.job = this.trigger.app + '/' + this.trigger.pipeline;
    }
  }
}

export const WERCKER_TRIGGER = 'spinnaker.core.pipeline.config.trigger.wercker';
module(WERCKER_TRIGGER, [require('../trigger.directive.js').name])
  .config(() => {
    Registry.pipeline.registerTrigger({
      label: 'Wercker',
      description: 'Listens to a Wercker pipeline',
      key: 'wercker',
      controller: 'WerckerTriggerCtrl',
      controllerAs: '$ctrl',
      templateUrl: require('./werckerTrigger.html'),
      manualExecutionComponent: WerckerTriggerTemplate,
      validators: [
        {
          type: 'requiredField',
          fieldName: 'job',
          message: '<strong>pipeline</strong> is a required field on Wercker triggers.',
        },
        {
          type: 'serviceAccountAccess',
          message: `You do not have access to the service account configured in this pipeline's Wercker trigger.
                    You will not be able to save your edits to this pipeline.`,
          preventSave: true,
        },
      ],
    });
  })
  .controller('WerckerTriggerCtrl', WerckerTrigger);
