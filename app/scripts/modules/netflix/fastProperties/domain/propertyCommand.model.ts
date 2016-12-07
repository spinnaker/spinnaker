
import {Scope} from '../domain/scope.domain';
import {Property} from '../domain/property.domain';
import {PropertyPipeline} from './propertyPipeline.domain';
import {PropertyStrategy} from './propertyStrategy.domain';
import {PropertyCommandType} from './propertyCommandType.enum';
import {IUser} from 'core/authentication/authentication.service';
import {IAccount} from 'core/account/account.service';
import {IPlatformProperty} from './platformProperty.model';

export class PropertyCommand {
  public property: Property;
  public scope: Scope;
  public pipeline: PropertyPipeline;
  public strategy: PropertyStrategy;
  public type: PropertyCommandType;
  public user: IUser;
  public accounts: IAccount[];
  public regions: any;
  public applicationName: string;

  constructor() {
    this.property = new Property('prod');
  }

  isReadyForStrategy() {
    return !!(this.property && this.scope);
  }

  isReadyForPipeline() {
    return this.isReadyForStrategy && this.strategy;
  }

  buildPropertyAndScope(platformProperty: IPlatformProperty) {
    this.property = Property.build(platformProperty);
    this.scope = Scope.build(platformProperty);
  }

  getTypeAsString() {
    return PropertyCommandType[this.type];
  }

  submitButtonLabel() {
    let typeAsString = this.getTypeAsString();
    return typeAsString
              ? `${this.getTypeAsString().charAt(0).toUpperCase()}${this.getTypeAsString().slice(1).toLowerCase()}`
              : 'Submit';
  }
}
