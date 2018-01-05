import * as React from 'react';
import * as ReactDOM from 'react-dom';
import { Action } from 'redux';
import { connect } from 'react-redux';
import { get } from 'lodash';
import { ICanaryState } from '../../reducers/index';
import { ICanaryMetricConfig } from '../../domain/ICanaryConfig';
import { UPDATE_ATLAS_QUERY } from '../../actions/index';
import { CanarySettings } from '../../canary.settings';
import autoBindMethods from 'class-autobind-decorator';

interface IAtlasMetricConfigurerStateProps {
  editingMetric: ICanaryMetricConfig;
}

interface IAtlasMetricConfigurerDispatchProps {
  updateQuery: (event: any) => void;
}

type IAtlasMetricConfigurerProps = IAtlasMetricConfigurerStateProps & IAtlasMetricConfigurerDispatchProps;

interface IAtlasMetricConfigurerState {
  webComponent: Element;
}

export interface AtlasQuerySelector {
  'class': string;
  backends: string;
  query: string;
  hide: string;
  ref: (webComponent: Element) => void;
}

interface ChangeQueryStringEvent extends CustomEvent {
  detail: string;
}

if (CanarySettings.atlasWebComponentsUrl) {
  // make React available to components
  const global = window as any;
  global['React'] = React;
  global['ReactDOM'] = ReactDOM;
  // download components; they will register when the script executes
  const script = document.createElement('script');
  script.src = CanarySettings.atlasWebComponentsUrl;
  document.head.appendChild(script);
}

// Add <atlas-query-selector> to the elements allowed in TSX, using the AtlasQuerySelector interface.
declare global {
  namespace JSX {
    interface IntrinsicElements {
      'atlas-query-selector': AtlasQuerySelector
    }
  }
}

const queryFinder = (metric: ICanaryMetricConfig) => get(metric, 'query.q', '');

/*
 * Component for configuring an Atlas metric via the <atlas-query-selector> web component.
 */
@autoBindMethods
class AtlasMetricConfigurer extends React.Component<IAtlasMetricConfigurerProps, IAtlasMetricConfigurerState> {

  constructor() {
    super();
    this.state = { webComponent: null };
  }

  private bindComponent(ref: Element) {
    this.setState({ webComponent: ref });
    if (ref) {
      ref.addEventListener('change.queryString', (event: ChangeQueryStringEvent) => {
        this.props.updateQuery(event.detail);
      });
      // This gets passed down to the inner React rendering beyond the web-component wrapper. Simply setting it in
      // the render() method, it will not get passed to the web-component. Given that it doesn't change, why is it
      // necessary? It seems to have something to do with the way React renders the component to the DOM; it triggers
      // the web-component layer early, before the query is ready, and <atlas-query-selector> ignores all changes to
      // the query after it's rendered. Adding a key after it's all set causes the inner React to re-render the
      // component with the correct query.
      ref.setAttribute('key', 'react');
    }
  }

  public render() {
    const editingMetric = this.props.editingMetric;
    const query = queryFinder(editingMetric);
    // TODO: select correct Atlas backend for app
    const atlasBackend = 'https://atlas-global.prod.netflix.net';
    return (
      <atlas-query-selector
        class="spinnaker-theme"
        backends={atlasBackend}
        hide="add_scope,comparison,timeOffset,lineStyle,des,trend,lineWidth,axis,color,alpha,multi"
        query={query}
        ref={this.bindComponent}
      />
    );
  }

}

function mapStateToProps(state: ICanaryState): IAtlasMetricConfigurerStateProps {
  return {
    editingMetric: state.selectedConfig.editingMetric,
  }
}

function mapDispatchToProps(dispatch: (action: Action & any) => void): IAtlasMetricConfigurerDispatchProps {
  return {
    updateQuery: (query: string): void => {
      dispatch({
        type: UPDATE_ATLAS_QUERY,
        query
      });
    },
  };
}

export default connect(mapStateToProps, mapDispatchToProps)(AtlasMetricConfigurer);

export { queryFinder };
