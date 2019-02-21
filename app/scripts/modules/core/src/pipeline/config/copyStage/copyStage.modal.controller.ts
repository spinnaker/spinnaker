import { flatten } from 'lodash';
import { IController, IPromise, IQService, module } from 'angular';
import { IModalInstanceService } from 'angular-ui-bootstrap';

import { API } from 'core/api/ApiService';
import { Application } from 'core/application/application.model';
import { ApplicationReader, IApplicationSummary } from 'core/application/service/ApplicationReader';
import { COPY_STAGE_CARD_COMPONENT } from './copyStageCard.component';
import { IPipeline, IStage, IStrategy } from 'core/domain';

import './copyStage.modal.less';

interface IStageWrapper {
  pipeline?: string;
  strategy?: string;
  stage: IStage;
}

class CopyStageModalCtrl implements IController {
  public applications: IApplicationSummary[];
  public stages: IStageWrapper[];
  public viewState = { loading: true, error: false };
  public selectedStage: IStageWrapper;

  private uncopiableStageTypes: Set<string> = new Set(['deploy']);

  public static $inject = ['$q', 'application', '$uibModalInstance', 'forStrategyConfig'];
  constructor(
    private $q: IQService,
    public application: Application,
    private $uibModalInstance: IModalInstanceService,
    private forStrategyConfig: boolean,
  ) {}

  public $onInit(): void {
    this.$q
      .all({
        stages: this.getStagesForApplication(this.application.name),
        applications: ApplicationReader.listApplications(),
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

  public onApplicationSelect(selected: Application): void {
    this.viewState.loading = true;
    this.getStagesForApplication(selected.name)
      .then(stages => {
        this.stages = stages;
        this.viewState.loading = false;
        this.viewState.error = false;
      })
      .catch(() => {
        this.viewState.loading = false;
        this.viewState.error = true;
      });
  }

  public cancel(): void {
    this.$uibModalInstance.dismiss();
  }

  public copyStage(): void {
    this.$uibModalInstance.close(this.selectedStage.stage);
  }

  private getStagesForApplication(applicationName: string): IPromise<IStageWrapper[]> {
    const configType = this.forStrategyConfig ? 'strategyConfigs' : 'pipelineConfigs';

    return API.one('applications')
      .one(applicationName)
      .all(configType)
      .getList()
      .then((configs: Array<IPipeline | IStrategy>) => {
        const nestedStageWrappers = configs.map(config => {
          return (config.stages || [])
            .filter((stage: IStage) => !this.uncopiableStageTypes.has(stage.type))
            .map((stage: IStage) => {
              if (this.isStrategyConfig(config)) {
                return { strategy: config.name, stage };
              } else {
                return { pipeline: config.name, stage };
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

module(COPY_STAGE_MODAL_CONTROLLER, [COPY_STAGE_CARD_COMPONENT]).controller('CopyStageModalCtrl', CopyStageModalCtrl);
