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
  public scopes: Scope[] = [];
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
    return !!(this.property && this.scopes);
  }

  public isReadyForPipeline(): boolean {
    return this.isReadyForStrategy() && !!this.strategy;
  }

  public buildPropertyAndScope(platformProperty: IPlatformProperty) {
    this.property = Property.build(platformProperty);
    this.scopes = [Scope.build(platformProperty)];
    this.originalScope = Scope.build(platformProperty);
  }

  public buildPropertyStages(user: IUser): PropertyPipelineStage[] {
    let stages: PropertyPipelineStage[] = [];
    if (this.isMoveToNewScope()) {
      let createStage = PropertyPipelineStage.newPropertyStage(user, this.scopes[0], this);
      let deleteStage = PropertyPipelineStage.deletePropertyStage(user, this, createStage);
      stages = [createStage, deleteStage];
    } else {
      this.scopes.reduce((previousStage: PropertyPipelineStage, scope: Scope) => {
        let stage = PropertyPipelineStage.upsertPropertyStage(user, scope, this, previousStage);
        stages.push(stage);
        return stage;
      }, null);
    }

    return stages;
  }

  public originalScopeForSubmit(): Scope {
    return this.originalScope ? this.originalScope.forSubmit(this.property.env) : null;
  }

  public getCombinedInstanceCountsForAllScopes(): number {
    return this.scopes.reduce((count: number, scope: Scope) =>  count + scope.instanceCounts.up, 0);
  }

  public isMoveToNewScope(): boolean {
    if (this.scopes.length > 0 && this.originalScope) {
      return !isEqual(
        omit(this.scopes[0], ['instanceCounts']),
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
