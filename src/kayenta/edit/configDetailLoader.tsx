import * as React from 'react';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { Observable } from 'rxjs/Observable';
import { Subscription } from 'rxjs/Subscription';

import ConfigDetailLoadStates from './configDetailLoadStates';
import * as Creators from '../actions/creators';

interface IConfigLoaderStateParamsProps {
  configNameStream: Observable<IConfigDetailStateParams>;
}

interface IConfigLoaderDispatchProps {
  loadConfig: (stateParams: IConfigDetailStateParams) => void;
}

interface IConfigDetailStateParams {
  id: string;
  copy: boolean;
  'new': boolean;
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
      if (stateParams.copy) {
        dispatch(Creators.copySelectedConfig());
      } else if (stateParams.new) {
        dispatch(Creators.createNewConfig());
      } else if (stateParams.id) {
        dispatch(Creators.loadConfigRequest({ id: stateParams.id }));
      }
    }
  };
}

export default connect(null, mapDispatchToProps)(ConfigDetailLoader);
