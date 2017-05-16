import {IGceAutoHealingPolicy} from './autoHealingPolicy';
import { IServerGroup } from 'core/domain';

// TODO(dpeach): fill in the remaining GCE specific properties.
export interface IGceServerGroup extends IServerGroup {
  autoHealingPolicy?: IGceAutoHealingPolicy;
}
