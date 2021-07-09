import { IController, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import { WerckerExecutionLabel } from './WerckerExecutionLabel';
import { BuildServiceType, IgorService } from '../../../../ci/igor.service';
import { IJobConfig, IParameterDefinitionList, IStage } from '../../../../domain';
import { Registry } from '../../../../registry';

export interface IWerckerStageViewState {
  mastersLoaded: boolean;
  mastersRefreshing: boolean;
  appsLoaded: boolean;
  appsRefreshing: boolean;
  failureOption?: string;
  markUnstableAsSuccessful?: boolean;
  waitForCompletion?: boolean;
  masterIsParameterized?: boolean;
  jobIsParameterized?: boolean;
}

export interface IParameter {
  key: string;
  value: string;
}

export class WerckerStage implements IController {
  public viewState: IWerckerStageViewState;
  public useDefaultParameters: any;
  public userSuppliedParameters: any;
  public masters: string[];
  public jobs: string[];
  public jobParams: IParameterDefinitionList[];
  public apps: string[];
  public pipelines: string[];
  public filterLimit = 100;
  private filterThreshold = 500;
  public app: string;
  public pipeline: string;
  public job: string;

  public static $inject = ['stage', '$scope', '$uibModal'];
  constructor(public stage: any, $scope: IScope, private $uibModal: IModalService) {
    this.stage.failPipeline = this.stage.failPipeline === undefined ? true : this.stage.failPipeline;
    this.stage.continuePipeline = this.stage.continuePipeline === undefined ? false : this.stage.continuePipeline;
    this.viewState = {
      mastersLoaded: false,
      mastersRefreshing: false,
      appsLoaded: false,
      appsRefreshing: false,
      failureOption: 'fail',
      markUnstableAsSuccessful: !!this.stage.markUnstableAsSuccessful,
      waitForCompletion: this.stage.waitForCompletion || this.stage.waitForCompletion === undefined,
    };
    this.useDefaultParameters = {};
    this.userSuppliedParameters = {};

    this.initializeMasters();

    $scope.$watch('stage.master', () => this.updateAppsList());
    $scope.$watch('stage.app', () => this.updateJobsList());
    $scope.$watch('stage.pipeline', () => this.updateJob());
    $scope.$watch('stage.job', () => this.updateJobConfig());
  }

  // Using viewState to avoid marking pipeline as dirty if field is not set
  public markUnstableChanged(): void {
    this.stage.markUnstableAsSuccessful = this.viewState.markUnstableAsSuccessful;
  }

  public waitForCompletionChanged(): void {
    this.stage.waitForCompletion = this.viewState.waitForCompletion;
  }

  public refreshMasters(): void {
    this.viewState.mastersRefreshing = true;
    this.initializeMasters();
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
    const master = this.stage.master;
    const job: string = this.stage.job || '';
    const viewState = this.viewState;
    viewState.masterIsParameterized = master.includes('${');
    viewState.jobIsParameterized = job.includes('${');
    if (viewState.masterIsParameterized || viewState.jobIsParameterized) {
      viewState.appsLoaded = true;
      return;
    }
    if (this.stage && this.stage.master) {
      this.viewState.appsLoaded = false;
      this.apps = [];
      IgorService.listJobsForMaster(this.stage.master).then((jobs) => {
        this.viewState.appsLoaded = true;
        this.viewState.appsRefreshing = false;
        const apps = Object.create({});
        jobs.forEach(function (app) {
          const orgApp = app.substring(app.indexOf('/') + 1, app.lastIndexOf('/'));
          apps[orgApp] = orgApp;
        });
        this.apps = Object.keys(apps);
        this.jobs = jobs;
        if (this.apps.length && !this.apps.includes(this.stage.app)) {
          this.stage.app = '';
          this.stage.pipeline = '';
          this.stage.job = '';
        }
      });
    }
  }

  public refreshJobs(): void {
    this.viewState.appsRefreshing = true;
    this.updateJobsList();
  }

  private updateJobsList(): void {
    if (this.stage && this.stage.app) {
      this.pipelines = [];
      this.viewState.appsLoaded = true;
      this.viewState.appsRefreshing = false;
      if (this.jobs) {
        const pipelines = Object.create({});
        const appSelected = this.stage.app;
        this.jobs.forEach(function (app) {
          if (
            !app.startsWith('pipeline') &&
            appSelected === app.substring(app.indexOf('/') + 1, app.lastIndexOf('/'))
          ) {
            const pl = app.substring(app.lastIndexOf('/') + 1);
            pipelines[pl] = pl;
          }
        });
        this.pipelines = Object.keys(pipelines);
      }
      if (this.pipelines.length && !this.pipelines.includes(this.stage.pipeline)) {
        this.stage.pipeline = '';
        this.stage.job = '';
      }
    }
  }

  private updateJob(): void {
    if (this.stage && this.stage.app && this.stage.pipeline) {
      this.stage.job = this.stage.app + '/' + this.stage.pipeline;
    }
  }

  private updateJobConfig(): void {
    const stage = this.stage;
    const view = this.viewState;
    if (stage && stage.job && stage.master && !view.masterIsParameterized && !view.jobIsParameterized) {
      IgorService.getJobConfig(stage.master, stage.job).then((config: IJobConfig) => {
        config = config || ({} as IJobConfig);
        if (!stage.parameters) {
          stage.parameters = {};
        }
        this.jobParams = config.parameterDefinitionList;
        this.userSuppliedParameters = stage.parameters;
        this.useDefaultParameters = {};
        const params = this.jobParams || ([] as IParameterDefinitionList[]);
        params.forEach((property: any) => {
          if (!(property.name in stage.parameters) && property.defaultValue !== null) {
            this.useDefaultParameters[property.name] = true;
          }
        });
      });
    }
  }

  public addParameter(): void {
    this.$uibModal
      .open({
        templateUrl: require('./modal/addParameter.html'),
        controller: 'WerckerStageAddParameterCtrl',
        controllerAs: 'ctrl',
      })
      .result.then((parameter: IParameter) => {
        this.stage.parameters[parameter.key] = parameter.value;
      })
      .catch(() => {});
  }

  public removeParameter(key: string): void {
    delete this.stage.parameters[key];
  }

  public shouldFilter(): boolean {
    return this.jobs && this.jobs.length >= this.filterThreshold;
  }
}

export const WERCKER_STAGE = 'spinnaker.core.pipeline.stage.werckerStage';

module(WERCKER_STAGE, [])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Wercker',
      description: 'Runs a Wercker build pipeline',
      key: 'wercker',
      restartable: true,
      controller: 'WerckerStageCtrl',
      controllerAs: '$ctrl',
      templateUrl: require('./werckerStage.html'),
      executionDetailsUrl: require('./werckerExecutionDetails.html'),
      executionLabelComponent: WerckerExecutionLabel,
      extraLabelLines: (stage: IStage) => {
        if (!stage.masterStage.context || !stage.masterStage.context.buildInfo) {
          return 0;
        }
        const lines = stage.masterStage.context.buildInfo.number ? 1 : 0;
        return lines + (stage.masterStage.context.buildInfo.testResults || []).length;
      },
      supportsCustomTimeout: true,
      validators: [{ type: 'requiredField', fieldName: 'job' }],
      strategy: true,
    });
  })
  .controller('WerckerStageCtrl', WerckerStage);
