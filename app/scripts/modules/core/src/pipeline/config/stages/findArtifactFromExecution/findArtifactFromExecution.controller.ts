import { IController, IScope } from 'angular';

import { ApplicationReader } from 'core/application/service/ApplicationReader';
import { PipelineConfigService } from 'core/pipeline/config/services/PipelineConfigService';
import { UUIDGenerator } from 'core/utils/uuid.service';
import { IArtifact, IExpectedArtifact, IPipeline } from 'core/domain';

export interface IFindArtifactFromExecutionStage {
  application: string;
  pipeline: string;
  executionOptions: {
    successful: boolean;
    terminal?: boolean;
    running?: boolean;
  };
  expectedArtifact: IExpectedArtifact;
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
    if (this.$scope.stage.isNew) {
      this.stage.executionOptions = {
        successful: true,
      };

      this.stage.expectedArtifact = {
        id: UUIDGenerator.generateUuid(),
        matchArtifact: {
          kind: 'custom',
        } as IArtifact,
      } as IExpectedArtifact;
    }

    this.loadApplications();
    this.loadPipelines();
  }

  private loadApplications() {
    ApplicationReader.listApplications().then(apps => {
      this.state.applications = apps.map(a => a.name);
      this.state.applicationsLoaded = true;
    });
  }

  private loadPipelines() {
    this.state.pipelinesLoaded = false;
    if (this.stage.application) {
      PipelineConfigService.getPipelinesForApplication(this.stage.application).then(ps => {
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
