import { IStage, IUser } from '@spinnaker/core';

import { AcaTaskStage } from './acaTaskStage';
import { AcaTaskStageConfigDetails } from './acaTaskStageConfigDetails.model';
import { ManualJudgementStage } from './manualJudgementStage';
import { PropertyCommand } from './propertyCommand.model';

export interface PropertyStrategy {
  name: string;
  displayName: string;
  description: string;
  configDetails: any;
  form: string;

  isForcePush(): boolean;
  isManual(): boolean;
  isAca(): boolean;
  buildStage(user: IUser, command: PropertyCommand, previousStage?: IStage): IStage;
}

export class ManualStrategy implements PropertyStrategy {
  public name = 'ManualStrategy';
  public displayName = 'Manual Judgement Strategy';
  public description = `Manual Strategy will add a Manual Judgement Stage after each Property Stage`;
  public configDetails: any = {};

  public buildStage(user: IUser, _command: PropertyCommand, previousStage?: IStage): IStage {
    return new ManualJudgementStage(user, previousStage);
  }

  public isForcePush() {
    return false;
  }

  public isManual() {
    return true;
  }

  public isAca() {
    return false;
  }

  constructor(public form: string) {}
}

export class AcaStrategy implements PropertyStrategy {
  public name = 'AcaStrategy';
  public displayName = 'ACA Strategy';
  public description = `Aca Strategy will add an ACA Task stage after each Property Stage`;
  public configDetails: AcaTaskStageConfigDetails = new AcaTaskStageConfigDetails();

  public buildStage(user: IUser, command: PropertyCommand, previousStage?: IStage): IStage {
    return new AcaTaskStage(user, command, this.configDetails, previousStage);
  }

  public isForcePush() {
    return false;
  }

  public isManual() {
    return false;
  }

  public isAca() {
    return true;
  }

  constructor(public form: string) {}
}

export class ForcePushStrategy implements PropertyStrategy {
  public name = 'ForcePushStrategy';
  public displayName = 'Force Push Strategy';
  public description = `Force Push Strategy will not put any safeguards in place and will push the Property change through.`;
  public configDetails: any = {};

  public buildStage(_user: IUser, _command: PropertyCommand, _previousStage?: IStage): IStage {
    return undefined;
  }

  public isForcePush() {
    return true;
  }

  public isManual() {
    return false;
  }

  public isAca() {
    return false;
  }

  constructor(public form: string) {}
}
