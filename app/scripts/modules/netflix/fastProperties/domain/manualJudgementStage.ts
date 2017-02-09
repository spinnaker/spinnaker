import {toInteger} from 'lodash';
import {IStage} from 'core/domain/IStage';
import {IUser} from 'core/authentication/authentication.service';

export class ManualJudgementStage implements IStage {
  name: string;
  type: string;
  refId: (string | number);
  requisiteStageRefIds: (string | number)[] = [];
  notifications: any[] = [];
  judgmentInputs: any[] = [];
  failPipeline = true;
  instructions = 'Is Fast Property good to move forward?';
  propagateAuthenticationContext = true;

  constructor(user: IUser, previousStage?: IStage) {
    this.refId = previousStage ? `${toInteger(previousStage.refId) + 1}` : '1';
    this.requisiteStageRefIds = previousStage ? [previousStage.refId] : [];

    this.type = 'manualJudgment';
    this.name = 'Manual Judgment';
    this.notifications = [{ 'type': 'email', 'address': user.name}];
  }

}
