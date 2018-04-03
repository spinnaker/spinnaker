import { combineReducers, Action } from 'redux';
import { combineActions, handleActions } from 'redux-actions';
import { isEqual, chain } from 'lodash';

import * as Actions from 'kayenta/actions';
import * as Creators from 'kayenta/actions/creators';
import { IDataState, data } from './data';
import { app, IAppState } from './app';
import {
  ISelectedConfigState,
  selectedConfig
} from './selectedConfig';
import { JudgeSelectRenderState } from 'kayenta/edit/judgeSelect';
import { IJudge, ICanaryJudgeConfig, ICanaryAnalysisResult, KayentaAccountType, ICanaryMetricConfig } from 'kayenta/domain';
import { mapStateToConfig } from 'kayenta/service/canaryConfig.service';
import { ISelectedRunState, selectedRun } from './selectedRun';
import { metricResultsSelector } from 'kayenta/selectors';
import { validationErrorsReducer } from './validators';
import { AsyncRequestState } from './asyncRequest';
import { CanarySettings } from 'kayenta/canary.settings';

export interface ICanaryState {
  app: IAppState;
  data: IDataState;
  selectedConfig: ISelectedConfigState;
  selectedRun: ISelectedRunState;
}

const combined = combineReducers<ICanaryState>({
  app,
  configValidationErrors: () => null,
  data,
  selectedConfig,
  selectedRun,
});

const judgeRenderStateReducer = handleActions({
  [combineActions(Actions.SELECT_CONFIG, Actions.UPDATE_JUDGES)]: (state: ICanaryState) => {
    // At this point, we've already passed through the rest of the reducers, so
    // the judge has been normalized on the state.
    const {
      data: { judges = [] },
      selectedConfig: { judge: { judgeConfig } },
    } = state;

    if (!judgeConfig) {
      return state;
    } else {
      return {
        ...state,
        selectedConfig: {
          ...state.selectedConfig,
          judge: {
            ...state.selectedConfig.judge,
            renderState: getRenderState(judges, judgeConfig),
          }
        }
      }
    }
  }
}, null);

const getRenderState = (judges: IJudge[], judgeConfig: ICanaryJudgeConfig): JudgeSelectRenderState => {
  if (judges.some(judge => judge.name === judgeConfig.name)) {
    if (judges.length === 1) {
      return JudgeSelectRenderState.None;
    } else {
      return JudgeSelectRenderState.Multiple;
    }
  } else {
    return JudgeSelectRenderState.Single;
  }
};

const isInSyncWithServerReducer = (state: ICanaryState): ICanaryState => {
  return {
    ...state,
    selectedConfig: {
      ...state.selectedConfig,
      isInSyncWithServer: (() => {
        const editedConfig = mapStateToConfig(state);
        if (!editedConfig) {
          return true;
        } else {
          const originalConfig = state.data.configs.find(c => c.id === editedConfig.id);
          // If we're saving the config right now, don't warn that
          // the config hasn't been saved.
          if (!originalConfig
              && editedConfig.isNew
              && state.selectedConfig.save.state === AsyncRequestState.Requesting) {
            return true;
          }
          return isEqual(editedConfig, originalConfig);
        }
      })(),
    },
  }
};

const resolveSelectedMetricId = (state: ICanaryState, action: Action & any): string => {
  switch (action.type) {
    case Actions.SELECT_REPORT_METRIC:
      return action.payload.metricId;

    // On report load, pick the first metric.
    case Actions.LOAD_RUN_SUCCESS:
      return metricResultsSelector(state).length
        ? metricResultsSelector(state)[0].id
        : null;

    // On group select, pick the first metric in the group.
    case Actions.SELECT_REPORT_METRIC_GROUP:
      const results = metricResultsSelector(state);
      if (!results.length) {
        return null;
      }

      const group = action.payload.group;
      let filter: (r: ICanaryAnalysisResult) => boolean;
      if (!group) {
        filter = () => true;
      } else {
        filter = r => r.groups.includes(group);
      }

      return results.find(filter)
        ? results.find(filter).id
        : null;

    default:
      return null;
  }
};

const selectedMetricReducer = (state: ICanaryState, action: Action & any) => {
  if (![Actions.SELECT_REPORT_METRIC,
        Actions.SELECT_REPORT_METRIC_GROUP,
        Actions.LOAD_RUN_SUCCESS].includes(action.type)) {
    return state;
  }

  const id = resolveSelectedMetricId(state, action);
  if (!id) {
    return state;
  }

  // Load metric set pair.
  action.asyncDispatch(Creators.loadMetricSetPairRequest({
    pairId: id,
  }));

  return {
    ...state,
    selectedRun: {
      ...state.selectedRun,
      selectedMetric: id,
      metricSetPair: {
        ...state.selectedRun.metricSetPair,
        load: AsyncRequestState.Requesting,
      },
    },
  };
};

// TODO(dpeach): this assumes that a config can only use one metric store.
// If that changes, then metric store handling will have to be done per metric.
const selectedMetricStoreReducer = (state: ICanaryState, action: Action & any) => {
  switch (action.type) {
    case Actions.SELECT_CONFIG:
    case Actions.LOAD_KAYENTA_ACCOUNTS_SUCCESS: {
      const stores = chain(state.data.kayentaAccounts.data)
        .filter(account => account.supportedTypes.includes(KayentaAccountType.MetricsStore))
        .map(account => account.metricsStoreType || account.type)
        .uniq()
        .valueOf();

      let selectedStore =
        (state.selectedConfig.metricList || [])
          .map(metric => metric.query.type)
          .find(store => !!store);

      selectedStore = selectedStore || (
        stores.length
          ? (stores.includes(CanarySettings.metricStore)
               ? CanarySettings.metricStore
               : stores[0])
          : null);

      return {
        ...state,
        selectedConfig: {
          ...state.selectedConfig,
          selectedStore,
        },
      };
    }

    case Actions.SELECT_METRIC_STORE: {
      const selectedStore: string = action.payload.store;
      // Flips all metrics to use the selected metric store.
      const metricUpdater = (metric: ICanaryMetricConfig): ICanaryMetricConfig => ({
        ...metric,
        query: { ...metric.query, type: selectedStore, serviceType: selectedStore, },
      });

      return {
        ...state,
        selectedConfig: {
          ...state.selectedConfig,
          metricList: state.selectedConfig.metricList.map(metricUpdater),
          selectedStore,
        },
      };
    }

    default:
      return state;
  }
};

const disableConfigEditReducer = (state: ICanaryState) => {
  if (!state.selectedConfig.config) {
    return state;
  }

  return {
    ...state,
    app: {
      ...state.app,
      disableConfigEdit: !state.selectedConfig.config.applications.includes(state.data.application.name),
    }
  }
};

export const rootReducer = (state: ICanaryState, action: Action & any): ICanaryState => {
  return [
    combined,
    judgeRenderStateReducer,
    selectedMetricStoreReducer,
    selectedMetricReducer,
    disableConfigEditReducer,
    validationErrorsReducer,
    isInSyncWithServerReducer,
  ].reduce((s, reducer) => reducer(s, action), state);
};
