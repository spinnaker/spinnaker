import {IPlatformProperty} from './platformProperty.model';
import {Scope} from './scope.domain';

export class Property {
  [key: string]: any;
  public propertyId: string;
  public env = 'prod';
  public sourceOfUpdate = 'spinnaker';
  public updatedBy: string;
  public constraints: string;
  public description: string;
  public key: string;
  public value: string;
  public email: string;
  public cmcTicket: string;
  public region?: string;
  public stack?: string;

  public appId: string;
  public scope: Scope;
  public comment?: string;
  public stringVal?: string;

  public get baseOfScope(): string {
    const scope = this.scope || {} as any;
    if (scope.serverId) { return scope.serverId; }
    if (scope.zone) { return scope.zone; }
    if (scope.asg) { return scope.asg; }
    if (scope.cluster) { return scope.cluster; }
    if (scope.stack) { return scope.stack; }
    if (scope.region) { return scope.region; }
    if (scope.appId) { return scope.appId; }
    if (scope.app) { return scope.app; }
    return 'GLOBAL';
  }

  public static build(platformProperty: IPlatformProperty): Property {
    const property = new Property();
    property.propertyId = platformProperty.propertyId;
    property.env = platformProperty.env;
    property.updatedBy = platformProperty.updatedBy;
    property.constraints = platformProperty.constraints;
    property.description = platformProperty.description;
    property.key = platformProperty.key;
    property.value = platformProperty.value;
    property.email = platformProperty.email;
    property.cmcTicket = platformProperty.updatedBy;

    return property;
  }

  public static copy(property: Property): Property {
    return Object.assign(new Property(), property);
  }

  public static from(fields: any): Property {
    const property = Object.assign(new Property(), fields);
    property.stringVal = JSON.stringify(fields);
    property.scope = Property.buildFastPropertyScopeFromId(property.propertyId);
    return property;
  }

  public static buildFastPropertyScopeFromId(propertyId: string): Scope {
    // Property Id is a pipe delimited key of that has most of the scope info in it.
    // $NAME|$APPLICATION|$ENVIRONMENT|$REGION||$STACK|$COUNTRY(|cluster=$CLUSTER)
    const scope: Scope = new Scope();
    scope.appId = 'All (Global)';
    if (propertyId) {
      const items = propertyId.split('|');
      scope.key = items[0];
      scope.appId = items[1];
      scope.env = items[2];
      scope.region = items[3];
      scope.stack = items[5];
      scope.cluster = items[7] ? items[7].split('=')[1] : '';
    }
    return scope;
  }

  constructor(env?: string) {
    this.env = env;
  }

  public isValid() {
    return (this.key && this.value && this.email);
  }
}
