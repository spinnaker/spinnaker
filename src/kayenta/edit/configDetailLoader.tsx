import * as React from 'react';
import { connect } from 'react-redux';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import ConfigDetailLoadStates from './configDetailLoadStates';
import { LOAD_CONFIG } from '../actions/index';

interface IConfigLoaderStateParamsProps {
  configNameStream: Observable<string>;
}

interface IConfigLoaderDispatchProps {
  loadConfig: (configName: string) => void;
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

function mapDispatchToProps(dispatch: any): IConfigLoaderDispatchProps {
  return {
    loadConfig: (configName: string) => {
      dispatch({
        type: LOAD_CONFIG,
        id: configName
      });
    }
  };
}

export default connect(null, mapDispatchToProps)(ConfigDetailLoader);
