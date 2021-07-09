import { IController, IScope, module } from 'angular';
import { IModalService } from 'angular-ui-bootstrap';

import { TravisExecutionLabel } from './TravisExecutionLabel';
import { BuildServiceType, IgorService } from '../../../../ci/igor.service';
import { IJobConfig, IParameterDefinitionList, IStage } from '../../../../domain';
import { Registry } from '../../../../registry';

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

export interface IParameter {
  key: string;
  value: string;
}

export class TravisStage implements IController {
  public viewState: ITravisStageViewState;
  public useDefaultParameters: any;
  public userSuppliedParameters: any;
  public masters: string[];
  public jobs: string[];
  public jobParams: IParameterDefinitionList[];
  public filterLimit = 100;
  private filterThreshold = 500;

  public static $inject = ['stage', '$scope', '$uibModal'];
  constructor(public stage: any, $scope: IScope, private $uibModal: IModalService) {
    this.stage.failPipeline = this.stage.failPipeline === undefined ? true : this.stage.failPipeline;
    this.stage.continuePipeline = this.stage.continuePipeline === undefined ? false : this.stage.continuePipeline;
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
  }

  public refreshJobs(): void {
    this.viewState.jobsRefreshing = true;
    this.updateJobsList();
  }

  private initializeMasters(): void {
    IgorService.listMasters(BuildServiceType.Travis).then((masters: string[]) => {
      this.masters = masters;
      this.viewState.mastersLoaded = true;
      this.viewState.mastersRefreshing = false;
    });
  }

  private updateJobsList(): void {
    const master = this.stage.master;
    const job: string = this.stage.job || '';
    const viewState = this.viewState;
    viewState.masterIsParameterized = master.includes('${');
    viewState.jobIsParameterized = job.includes('${');
    if (viewState.masterIsParameterized || viewState.jobIsParameterized) {
      viewState.jobsLoaded = true;
      return;
    }
    viewState.jobsLoaded = false;
    this.jobs = [];
    IgorService.listJobsForMaster(master).then((jobs: string[]) => {
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
        controller: 'TravisStageAddParameterCtrl',
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

export const TRAVIS_STAGE = 'spinnaker.core.pipeline.stage.travisStage';

module(TRAVIS_STAGE, [])
  .config(() => {
    Registry.pipeline.registerStage({
      label: 'Travis',
      description: 'Runs a Travis job',
      key: 'travis',
      restartable: true,
      controller: 'TravisStageCtrl',
      controllerAs: '$ctrl',
      producesArtifacts: true,
      templateUrl: require('./travisStage.html'),
      executionDetailsUrl: require('./travisExecutionDetails.html'),
      executionLabelComponent: TravisExecutionLabel,
      providesVersionForBake: true,
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
  .controller('TravisStageCtrl', TravisStage);
