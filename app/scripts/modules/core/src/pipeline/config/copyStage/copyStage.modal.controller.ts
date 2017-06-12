import { flatten } from 'lodash';
import { IComponentController, IPromise, IQService, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';

import { API_SERVICE, Api } from 'core/api/api.service';
import { Application } from 'core/application/application.model';
import {
  APPLICATION_READ_SERVICE, ApplicationReader,
  IApplicationSummary
} from 'core/application/service/application.read.service';
import { COPY_STAGE_CARD_COMPONENT } from './copyStageCard.component';
import { IPipeline, IStage, IStrategy } from 'core/domain';

import './copyStage.modal.less';

interface IStageWrapper {
  pipeline?: string;
  strategy?: string;
  stage: IStage;
}

class CopyStageModalCtrl implements IComponentController {
  public applications: IApplicationSummary[];
  public stages: IStageWrapper[];
  public viewState = { loading: true, error: false };
  public selectedStage: IStageWrapper;

  private uncopiableStageTypes: Set<string> = new Set(['deploy']);

  constructor (private $q: IQService,
               private API: Api,
               public application: Application,
               private applicationReader: ApplicationReader,
               private $uibModalInstance: IModalInstanceService,
               private forStrategyConfig: boolean) {
    'ngInject';
  }

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

  private getStagesForApplication (applicationName: string): IPromise<IStageWrapper[]> {
    const configType = this.forStrategyConfig ? 'strategyConfigs' : 'pipelineConfigs';

    return this.API.one('applications')
        .one(applicationName)
        .all(configType)
        .getList()
        .then((configs: (IPipeline | IStrategy)[]) => {
          const nestedStageWrappers = configs
            .map((config) => {
              return (config.stages || [])
                .filter((stage: IStage) => !this.uncopiableStageTypes.has(stage.type))
                .map((stage: IStage) => {
                  if (this.isStrategyConfig(config)) {
                    return {strategy: config.name, stage: stage};
                  } else {
                    return {pipeline: config.name, stage: stage};
                  }
                });
            });

          return flatten(nestedStageWrappers);
        });
  }

  private isStrategyConfig(config: IPipeline | IStrategy): boolean {
    return 'strategy' in config;
  }
}

export const COPY_STAGE_MODAL_CONTROLLER = 'spinnaker.core.copyStage.modal.controller';

module(COPY_STAGE_MODAL_CONTROLLER, [
    API_SERVICE,
    COPY_STAGE_CARD_COMPONENT,
    APPLICATION_READ_SERVICE,
  ])
  .controller('CopyStageModalCtrl', CopyStageModalCtrl);
