import {isEqual, omit} from 'lodash';
import {Scope} from '../domain/scope.domain';
import {Property} from '../domain/property.domain';
import {PropertyPipeline} from './propertyPipeline.domain';
import {PropertyStrategy} from './propertyStrategy.domain';
import {PropertyCommandType} from './propertyCommandType.enum';
import {IUser} from 'core/authentication/authentication.service';
import {IAccount} from 'core/account/account.service';
import {IPlatformProperty} from './platformProperty.model';
import {PropertyPipelineStage} from './propertyPipelineStage';

export class PropertyCommand {
  public property: Property;
  public originalProperty: Property;
  public scope: Scope;
  public originalScope: Scope;
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

  public isReadyForStrategy(): boolean {
    return !!(this.property && this.scope);
  }

  public isReadyForPipeline(): boolean {
    return this.isReadyForStrategy() && !!this.strategy;
  }

  public buildPropertyAndScope(platformProperty: IPlatformProperty) {
    this.property = Property.build(platformProperty);
    this.scope = Scope.build(platformProperty);
    this.originalScope = Scope.build(platformProperty);
  }

  public buildPropertyStages(user: IUser): PropertyPipelineStage[] {
    let stages: PropertyPipelineStage[] = [];
    if (this.isMoveToNewScope()) {
      let createStage = PropertyPipelineStage.newPropertyStage(user, this);
      let deleteStage = PropertyPipelineStage.deletePropertyStage(user, this, createStage);
      stages = [createStage, deleteStage];
    } else {
      stages.push(PropertyPipelineStage.upsertPropertyStage(user, this));
    }

    return stages;
  }

  public scopeForSubmit(): Scope {
    return this.scope.forSubmit(this.property.env);
  }

  public originalScopeForSubmit(): Scope {
    return this.originalScope ? this.originalScope.forSubmit(this.property.env) : this.scopeForSubmit();
  }


  public isMoveToNewScope(): boolean {
    if (this.scope && this.originalScope) {
      return !isEqual(
        omit(this.scope, ['instanceCounts']),
        omit(this.originalScope, ['instanceCounts'])
      );
    }
    return false;
  }

  public getTypeAsString(): string {
    return PropertyCommandType[this.type];
  }

  public submitButtonLabel(): string {
    let typeAsString = this.getTypeAsString();
    return typeAsString
              ? `${this.getTypeAsString().charAt(0).toUpperCase()}${this.getTypeAsString().slice(1).toLowerCase()}`
              : 'Submit';
  }
}
