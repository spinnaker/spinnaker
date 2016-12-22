import {ServerGroup} from 'core/domain/index';

export interface IAppengineServerGroup extends ServerGroup {
  disabled: boolean;
}