import { toInteger } from 'lodash';

import { IStage, IUser } from '@spinnaker/core';

export class ManualJudgementStage implements IStage {
  public name: string;
  public type: string;
  public refId: (string | number);
  public requisiteStageRefIds: (string | number)[] = [];
  public notifications: any[] = [];
  public judgmentInputs: any[] = [];
  public failPipeline = true;
  public instructions = 'Is Fast Property good to move forward?';
  public propagateAuthenticationContext = true;

  constructor(user: IUser, previousStage?: IStage) {
    this.refId = previousStage ? `${toInteger(previousStage.refId) + 1}` : '1';
    this.requisiteStageRefIds = previousStage ? [previousStage.refId] : [];

    this.type = 'manualJudgment';
    this.name = 'Manual Judgment';
    this.notifications = [{ 'type': 'email', 'address': user.name}];
  }

}
