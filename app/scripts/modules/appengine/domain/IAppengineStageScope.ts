import {IScope} from 'angular';

import {Application} from 'core/application/application.model';
import {IStageConstant} from 'core/pipeline/config/stages/stageConstants';
import {IAppengineAccount} from './IAppengineAccount';

export interface IAppengineStage {
  credentials: string;
  region: string;
  target?: string;
  cloudProvider: string;
  isNew?: boolean;
  interestingHealthProviderNames: string[];
}

export interface IAppengineStageScope extends IScope {
  accounts: IAppengineAccount[];
  targets: IStageConstant[];
  stage: IAppengineStage;
  application: Application;
}