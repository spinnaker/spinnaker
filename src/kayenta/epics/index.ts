import { Observable } from 'rxjs/Observable';
import 'rxjs/add/observable/concat';
import { createEpicMiddleware, combineEpics } from 'redux-observable';
import { getCanaryConfigById } from '../service/canaryConfig.service';
import {
  CONFIG_LOAD_ERROR,
  LOAD_CONFIG,
  SELECT_CONFIG
} from '../actions/index';

const selectConfigEpic = (action$: Observable<any>) =>
  action$
    .filter(action => action.type === LOAD_CONFIG)
    .concatMap(action =>
      getCanaryConfigById(action.id)
        .then(config => ({type: SELECT_CONFIG, config}))
        .catch(error => ({type: CONFIG_LOAD_ERROR, error}))
    );

const rootEpic = combineEpics(
  selectConfigEpic
);

export const epicMiddleware = createEpicMiddleware(rootEpic);
