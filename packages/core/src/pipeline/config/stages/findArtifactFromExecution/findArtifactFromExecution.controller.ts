import { IController, IScope } from 'angular';

import {
  ApplicationReader,
  ExpectedArtifactService,
  IExpectedArtifact,
  IPipeline,
  PipelineConfigService,
} from '../../../../index';

export interface IFindArtifactFromExecutionStage {
  application: string;
  pipeline: string;
  executionOptions: {
    successful: boolean;
    terminal?: boolean;
    running?: boolean;
  };
  expectedArtifact: IExpectedArtifact; // legacy
  expectedArtifacts: IExpectedArtifact[];
}

export class FindArtifactFromExecutionCtrl implements IController {
  public stage: IFindArtifactFromExecutionStage;
  public state = {
    applicationsLoaded: false,
    pipelinesLoaded: false,
    applications: [] as string[],
    pipelines: [] as IPipeline[],
    infiniteScroll: {
      numToAdd: 20,
      currentItems: 20,
    },
  };

  public static $inject = ['$scope'];
  constructor(private $scope: IScope) {
    this.stage = this.$scope.stage as IFindArtifactFromExecutionStage;
    if (!this.stage.executionOptions) {
      this.stage.executionOptions = {
        successful: true,
      };
    }

    // Prior versions of this stage didn't allow a default artifact, so existing
    // stages may have a partly-initialized expected artifact. To handle these
    // cases, default any fields that are not present in the artifact.
    const initialArtifact = ExpectedArtifactService.createEmptyArtifact();
    if (this.stage.expectedArtifact) {
      Object.assign(initialArtifact, this.stage.expectedArtifact);
    }

    // Prior versions of this stage accepted only one expected artifact.
    if (!Array.isArray(this.stage.expectedArtifacts)) {
      this.stage.expectedArtifacts = [initialArtifact];
    }

    this.loadApplications();
    this.loadPipelines();
  }

  private loadApplications() {
    ApplicationReader.listApplications().then((apps) => {
      this.state.applications = apps.map((a) => a.name);
      this.state.applicationsLoaded = true;
    });
  }

  private loadPipelines() {
    this.state.pipelinesLoaded = false;
    if (this.stage.application) {
      PipelineConfigService.getPipelinesForApplication(this.stage.application).then((ps) => {
        this.state.pipelines = ps;
        this.state.pipelinesLoaded = true;
      });
    }
  }

  public addMoreApplications() {
    this.state.infiniteScroll.currentItems += this.state.infiniteScroll.numToAdd;
  }

  public onApplicationSelect() {
    this.loadPipelines();
  }
}
