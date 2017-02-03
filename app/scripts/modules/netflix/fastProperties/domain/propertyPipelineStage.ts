import {toInteger} from 'lodash';
import { copy } from 'angular';
import {IStage} from 'core/domain/IStage';
import {PropertyCommand} from './propertyCommand.model';
import {Scope} from './scope.domain';
import {Property} from './property.domain';
import {IUser} from 'core/authentication/authentication.service';
import {PropertyCommandType} from './propertyCommandType.enum';

export class PropertyPipelineStage implements IStage {
  name: string;
  type: string;
  refId: string;
  email: string;
  cmcTicket: string;
  delete: boolean;
  scope: Scope;
  persistedProperties: Property[] = [];
  originalProperties: Property[] = [];
  requisiteStageRefIds: (string | number)[] = [];

  constructor(user: IUser, command: PropertyCommand, previousStage?: IStage) {
    let propertyCopy: Property = copy(command.property);
    propertyCopy.updatedBy = user.name;
    propertyCopy.cmcTicket = user.name;

    delete propertyCopy.env;


    this.refId = previousStage ? `${toInteger(previousStage.refId) + 1}` : '1';
    this.requisiteStageRefIds = previousStage ? [previousStage.refId] : [];
    this.type = 'createProperty';
    this.name = 'Persisted Properties';
    this.scope = command.scope.forSubmit(command.property.env);
    this.email = propertyCopy.email;
    this.cmcTicket = propertyCopy.cmcTicket;
    this.delete = command.type === PropertyCommandType.DELETE;

    this.originalProperties.push(command.originalProperty);
    this.persistedProperties.push(propertyCopy);
  }

}
