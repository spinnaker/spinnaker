import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import ConfigDetailLoadStates from './configDetailLoadStates';
import { LOAD_CONFIG_REQUEST } from '../actions/index';

interface IConfigLoaderStateParamsProps {
  configNameStream: Observable<IConfigDetailStateParams>;
}

interface IConfigLoaderDispatchProps {
  loadConfig: (stateParams: IConfigDetailStateParams) => void;
}

interface IConfigDetailStateParams {
  configName: string;
}

export enum ConfigDetailLoadState {
  Loaded,
  Loading,
  Error
}
/*
 * Top-level .configDetail state component.
 * Loads config details on changes to /canary/:configName path parameter, renders load states.
 */
class ConfigDetailLoader extends React.Component<IConfigLoaderDispatchProps & IConfigLoaderStateParamsProps> {

  private subscription: Subscription;

  constructor({ configNameStream, loadConfig }: IConfigLoaderDispatchProps & IConfigLoaderStateParamsProps) {
    super();
    this.subscription = configNameStream.subscribe(loadConfig);
  }

  public componentWillUnmount(): void {
    this.subscription.unsubscribe();
  }

  public render() {
    return <ConfigDetailLoadStates/>;
  }
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IConfigLoaderDispatchProps {
  return {
    loadConfig: (stateParams: IConfigDetailStateParams) => {
      if (stateParams.configName) {
        dispatch({
          type: LOAD_CONFIG_REQUEST,
          id: stateParams.configName,
        });
      }
    }
  };
}

export default connect(null, mapDispatchToProps)(ConfigDetailLoader);
