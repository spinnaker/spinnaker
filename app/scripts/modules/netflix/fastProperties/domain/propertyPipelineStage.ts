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
  description: string;

  static clonePropertyForStage(user: IUser, property: Property): Property {
    let propertyCopy: Property = copy(property);
    propertyCopy.updatedBy = user.name;
    propertyCopy.cmcTicket = user.name;

    delete propertyCopy.env;

    return propertyCopy;
  }

  static addOriginalProperty(stage: PropertyPipelineStage, command: PropertyCommand): IStage {
    if (command.originalProperty) {
      stage.originalProperties.push(command.originalProperty);
    }
    return stage;
  }

  public static newPropertyStage(user: IUser, command: PropertyCommand, previousStage?: IStage): PropertyPipelineStage {
    let property: Property = PropertyPipelineStage.clonePropertyForStage(user, command.property);
    property.propertyId = null;

    let scope: Scope = command.scopeForSubmit();

    let stage = new PropertyPipelineStage(property, scope, previousStage);
    stage.delete = false;
    stage.description = `Create new property for ${property.key}`;

    PropertyPipelineStage.addOriginalProperty(stage, command);

    return stage;
  }

  public static deletePropertyStage(user: IUser, command: PropertyCommand, previousStage?: IStage): PropertyPipelineStage {
    let property: Property = PropertyPipelineStage.clonePropertyForStage(user, command.property);
    let scope: Scope = command.originalScopeForSubmit();
    let stage = new PropertyPipelineStage(property, scope, previousStage);
    stage.delete = true;
    stage.description = `Deleting property for ${property.key}`;
    PropertyPipelineStage.addOriginalProperty(stage, command);

    return stage;
  }

  public static upsertPropertyStage(user: IUser, command: PropertyCommand, previousStage?: IStage): PropertyPipelineStage {
    let property: Property = PropertyPipelineStage.clonePropertyForStage(user, command.property);
    let scope: Scope = command.scopeForSubmit();
    let stage = new PropertyPipelineStage(property, scope, previousStage);
    stage.delete = command.type === PropertyCommandType.DELETE;
    stage.description = `Upserting property for ${property.key}`;
    PropertyPipelineStage.addOriginalProperty(stage, command);

    return stage;
  }


  constructor(property: Property, scope: Scope, previousStage?: IStage) {
    this.refId = previousStage ? `${toInteger(previousStage.refId) + 1}` : '1';
    this.requisiteStageRefIds = previousStage ? [previousStage.refId] : [];
    this.type = 'createProperty';
    this.name = 'Persisted Properties';
    this.email = property.email;
    this.cmcTicket = property.cmcTicket;

    this.scope = scope;
    this.persistedProperties.push(property);
  }


}
