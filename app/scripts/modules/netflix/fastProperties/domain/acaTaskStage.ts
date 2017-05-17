import { toInteger } from 'lodash';

import { IStage, IUser } from '@spinnaker/core';

import { AcaTaskStageConfigDetails } from './acaTaskStageConfigDetails.model';
import { Canary } from './canary.domain';
import { PropertyCommand } from './propertyCommand.model';

export class AcaTaskStage implements IStage {
  public name: string;
  public type: string;
  public refId: (string | number);
  public requisiteStageRefIds: (string|number)[] = [];
  public comments: string;
  public canary: Canary;

  constructor(user: IUser, command: PropertyCommand, configDetails: AcaTaskStageConfigDetails, previousStage?: IStage) {
    this.refId = previousStage ? `${toInteger(previousStage.refId) + 1}` : '1';
    this.requisiteStageRefIds = previousStage ? [previousStage.refId] : [];
    this.type = 'acaTask';
    this.name = 'ACA Task';
    this.canary = new Canary(user, command, configDetails);
  }
}
