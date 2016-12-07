import {IPlatformProperty} from './platformProperty.model';
import {CATEGORY} from '../scope/fastPropertyScopeSearchCategory.service';
import {IImpactCounts} from './impactCounts.interface';

export class Scope {
  [k: string]: any // Index signature
  public env: string;
  public region: string;
  public appId: string;
  public appIdList: string[] = [];
  public stack: string;
  public cluster: string;
  public asg: string;
  public serverId: string;
  public zone: string;
  public instanceCounts: IImpactCounts = <IImpactCounts>{
    down: 0,
    up: 0,
    failed: 0,
    outOfService: 0,
    starting: 0,
    succeeded: 0,
    total: 0,
    unknown: 0
  };

  static build(platformProperty: IPlatformProperty): Scope {
    return Object.assign(new Scope(), platformProperty);
  }

  public getBaseOfScope() {
    if (this.serverId) { return this.serverId; }
    if (this.zone) { return this.zone; }
    if (this.asg) { return this.asg; }
    if (this.cluster) { return this.cluster; }
    if (this.stack) { return this.stack; }
    if (this.region) { return this.region; }
    if (this.appId) { return this.appId; }
    return 'GLOBAL';
  }

  public getCategory() {
    if (this.serverId) { return CATEGORY.INSTANCES; }
    if (this.zone || this.asg) { return CATEGORY.SERVER_GROUPS; }
    if (this.cluster) { return CATEGORY.CLUSTERS; }
    if (this.stack || this.region) { return CATEGORY.REGIONS; }
    if (this.appId) { return CATEGORY.APPLICATIONS; }
    return CATEGORY.GLOBAL;
  }

  forSubmit(env: string): Scope {
    let copy: Scope = Object.assign({}, this);
    copy.env = env;
    copy.appIdList = [this.appId];
    delete copy.instanceCounts;
    delete copy.appId;
    return copy;
  }

}
