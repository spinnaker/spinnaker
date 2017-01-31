import {toInteger} from 'lodash';
import {IStage} from 'core/domain/IStage';
import {IUser} from 'core/authentication/authentication.service';
import {PropertyCommand} from './propertyCommand.model';
import {Canary} from './canary.domain';
import {AcaTaskStageConfigDetails} from './acaTaskStageConfigDetails.model';

export class AcaTaskStage implements IStage {
  public name: string;
  public type: string;
  public refId: (string | number);
  public requisiteStageRefIds: (string|number)[] = [];
  public comments: string;
  public canary: Canary;

  constructor(private user: IUser, private command: PropertyCommand, private configDetails: AcaTaskStageConfigDetails, private previousStage?: IStage) {
    this.refId = previousStage ? `${toInteger(previousStage.refId) + 1}` : '1';
    this.requisiteStageRefIds = previousStage ? [previousStage.refId] : [];
    this.type = 'acaTask';
    this.name = 'ACA Task';
    this.canary = new Canary(user, command, configDetails);
  }


}
