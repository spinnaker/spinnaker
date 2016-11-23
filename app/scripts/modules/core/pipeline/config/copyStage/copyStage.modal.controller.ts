import {flatten} from 'lodash';
import {module} from 'angular';

import {API_SERVICE, Api} from 'core/api/api.service';
import {Application} from 'core/application/application.model';
import {
  APPLICATION_READ_SERVICE, ApplicationReader,
  IApplicationSummary
} from 'core/application/service/application.read.service';
import {COPY_STAGE_CARD_COMPONENT} from './copyStageCard.component';
import {IPipeline, IStage} from 'core/domain/index';

import './copyStage.modal.less';

interface IStageWrapper {
  pipeline: string;
  stage: IStage;
}

class CopyStageModalCtrl implements ng.IComponentController {
  public applications: IApplicationSummary[];
  public stages: IStageWrapper[];
  public viewState = { loading: true, error: false };
  public selectedStage: IStageWrapper;

  private uncopiableStageTypes: Set<string> = new Set(['deploy']);

  static get $inject() { return ['$q', 'API', 'application', 'applicationReader', '$uibModalInstance']; }

  constructor (private $q: ng.IQService,
               private API: Api,
               public application: Application,
               private applicationReader: ApplicationReader,
               private $uibModalInstance: any) { }

  public $onInit (): void {
    this.$q.all({
      stages: this.getStagesForApplication(this.application.name),
      applications: this.applicationReader.listApplications(),
    })
    .then(({ stages, applications }) => {
      this.stages = stages;
      this.applications = applications;
      this.viewState.loading = false;
      this.viewState.error = false;
    })
    .catch(() => {
      this.viewState.loading = false;
      this.viewState.error = true;
    });
  }

  public onApplicationSelect (selected: Application): void {
    this.viewState.loading = true;
    this.getStagesForApplication(selected.name)
      .then((stages) => {
        this.stages = stages;
        this.viewState.loading = false;
        this.viewState.error = false;
      })
      .catch(() => {
        this.viewState.loading = false;
        this.viewState.error = true;
      });
  }

  public cancel (): void {
    this.$uibModalInstance.dismiss();
  }

  public copyStage (): void {
    this.$uibModalInstance.close(this.selectedStage.stage);
  }

  private getStagesForApplication (applicationName: string): ng.IPromise<IStageWrapper[]> {
    return this.API.one('applications')
      .one(applicationName)
      .all('pipelineConfigs')
      .getList()
      .then((pipelines: IPipeline[]) => {
        let nestedStageWrappers = pipelines
          .map((pipeline) => {
            return (pipeline.stages || [])
              .filter((stage: IStage) => !this.uncopiableStageTypes.has(stage.type))
              .map((stage: IStage) => {
                return { pipeline: pipeline.name, stage: stage };
              });
          });

        return flatten(nestedStageWrappers);
      });
  }
}

export const COPY_STAGE_MODAL_CONTROLLER = 'spinnaker.core.copyStage.modal.controller';

module(COPY_STAGE_MODAL_CONTROLLER, [
    API_SERVICE,
    COPY_STAGE_CARD_COMPONENT,
    APPLICATION_READ_SERVICE,
  ])
  .controller('CopyStageModalCtrl', CopyStageModalCtrl);
