import { Observable } from 'rxjs/Observable';
import 'rxjs/add/observable/concat';
import { createEpicMiddleware, combineEpics } from 'redux-observable';
import { getCanaryConfigById } from '../service/canaryConfig.service';

const selectConfigEpic = (action$: Observable<any>) =>
  action$
    .filter(action => action.type === 'load_config')
    .concatMap(action => getCanaryConfigById(action.id))
    .map(config => ({ type: 'select_config', config }));

const rootEpic = combineEpics(
  selectConfigEpic
);

export const epicMiddleware = createEpicMiddleware(rootEpic);
