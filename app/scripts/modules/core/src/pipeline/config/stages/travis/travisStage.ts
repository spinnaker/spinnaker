import {PIPELINE_CONFIG_PROVIDER} from 'core/pipeline/config/pipelineConfigProvider';
import {module, IScope} from 'angular';
import * as moment from 'moment';

import {SETTINGS} from 'core/config/settings';
import {IGOR_SERVICE, IgorService, BuildServiceType} from 'core/ci/igor.service';
import {IJobConfig, ParameterDefinitionList, IStage} from 'core/domain';
import {TravisExecutionLabel} from './TravisExecutionLabel';

export interface ITravisStageViewState {
  mastersLoaded: boolean;
  mastersRefreshing: boolean;
  jobsLoaded: boolean;
  jobsRefreshing: boolean;
  failureOption?: string;
  markUnstableAsSuccessful?: boolean;
  waitForCompletion?: boolean;
  masterIsParameterized?: boolean;
  jobIsParameterized?: boolean;
}

export class TravisStage {
  public viewState: ITravisStageViewState;
  public useDefaultParameters: any;
  public userSuppliedParameters: any;
  public masters: string[];
  public jobs: string[];
  public jobParams: ParameterDefinitionList[];
  public filterLimit = 100;
  private filterThreshold = 500;


  constructor(public stage: any,
              $scope: IScope,
              private igorService: IgorService) {
    this.stage.failPipeline = (this.stage.failPipeline === undefined ? true : this.stage.failPipeline);
    this.stage.continuePipeline = (this.stage.continuePipeline === undefined ? false : this.stage.continuePipeline);
    this.viewState = {
      mastersLoaded: false,
      mastersRefreshing: false,
      jobsLoaded: false,
      jobsRefreshing: false,
      failureOption: 'fail',
      markUnstableAsSuccessful: !!this.stage.markUnstableAsSuccessful,
      waitForCompletion: this.stage.waitForCompletion || this.stage.waitForCompletion === undefined,
    };
    this.useDefaultParameters = {};
    this.userSuppliedParameters = {};

    this.initializeMasters();
    $scope.$watch('stage.master', () => this.updateJobsList());
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
  };

  public refreshJobs(): void {
    this.viewState.jobsRefreshing = true;
    this.updateJobsList();
  };

  private initializeMasters(): void {
    this.igorService.listMasters(BuildServiceType.Travis).then((masters: string[]) => {
      this.masters = masters;
      this.viewState.mastersLoaded = true;
      this.viewState.mastersRefreshing = false;
    });
  }

  private updateJobsList(): void {
    const master = this.stage.master,
      job: string = this.stage.job || '',
      viewState = this.viewState;
    viewState.masterIsParameterized = master.includes('${');
    viewState.jobIsParameterized = job.includes('${');
    if (viewState.masterIsParameterized || viewState.jobIsParameterized) {
      viewState.jobsLoaded = true;
      return;
    }
    viewState.jobsLoaded = false;
    this.jobs = [];
    this.igorService.listJobsForMaster(master).then((jobs: string[]) => {
      this.viewState.jobsLoaded = true;
      this.viewState.jobsRefreshing = false;
      this.jobs = jobs;
      if (!this.jobs.includes(this.stage.job)) {
        this.stage.job = '';
      }
    });
    this.useDefaultParameters = {};
    this.userSuppliedParameters = {};
    this.jobParams = null;
  }

  private updateJobConfig(): void {
    const stage = this.stage;
    const view = this.viewState;
    if (stage && stage.job && stage.master && !view.masterIsParameterized && !view.jobIsParameterized) {
      this.igorService.getJobConfig(stage.master, stage.job).then((config: IJobConfig) => {
        config = config || <IJobConfig>{};
        if (!stage.parameters) {
          stage.parameters = {};
        }
        this.jobParams = config.parameterDefinitionList;
        this.userSuppliedParameters = stage.parameters;
        this.useDefaultParameters = {};
        const params = this.jobParams || <ParameterDefinitionList[]>[];
        params.forEach((property: any) => {
          if (!(property.name in stage.parameters) && (property.defaultValue !== null)) {
            this.useDefaultParameters[property.name] = true;
          }
        });
      });
    }
  }

  public updateParam(parameter: any): void {
    if (this.useDefaultParameters[parameter] === true) {
      delete this.userSuppliedParameters[parameter];
      delete this.stage.parameters[parameter];
    } else if (this.userSuppliedParameters[parameter]) {
      this.stage.parameters[parameter] = this.userSuppliedParameters[parameter];
    }
  };

  public shouldFilter(): boolean {
    return this.jobs && this.jobs.length >= this.filterThreshold;
  }
}

export const TRAVIS_STAGE = 'spinnaker.core.pipeline.stage.travisStage';

module(TRAVIS_STAGE, [
  IGOR_SERVICE,
  PIPELINE_CONFIG_PROVIDER
]).config((pipelineConfigProvider: any) => {

  if (SETTINGS.feature.travis) {
    pipelineConfigProvider.registerStage({
      label: 'Travis',
      description: 'Runs a Travis job',
      key: 'travis',
      restartable: true,
      controller: 'TravisStageCtrl',
      controllerAs: '$ctrl',
      templateUrl: require('./travisStage.html'),
      executionDetailsUrl: require('./travisExecutionDetails.html'),
      executionLabelComponent: TravisExecutionLabel,
      extraLabelLines: (stage: IStage) => {
        if (!stage.masterStage.context || !stage.masterStage.context.buildInfo) {
          return 0;
        }
        const lines = stage.masterStage.context.buildInfo.number ? 1 : 0;
        return lines + (stage.masterStage.context.buildInfo.testResults || []).length;
      },
      defaultTimeoutMs: moment.duration(2, 'hours').asMilliseconds(),
      validators: [
        {type: 'requiredField', fieldName: 'job'},
      ],
      strategy: true,
    });
  }

}).controller('TravisStageCtrl', TravisStage);
