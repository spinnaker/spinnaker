import {IGceAutoHealingPolicy} from './autoHealingPolicy';
import {ServerGroup} from 'core/domain/serverGroup';

// TODO(dpeach): fill in the remaining GCE specific properties.
export interface IGceServerGroup extends ServerGroup {
  autoHealingPolicy?: IGceAutoHealingPolicy;
}
