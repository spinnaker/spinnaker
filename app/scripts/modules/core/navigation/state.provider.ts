import {module} from 'angular';
import {IStateProvider, IUrlRouterProvider, IState} from 'angular-ui-router';
import {STATE_HELPER, StateHelper} from './stateHelper.provider';

require('./navigation.less');

export interface INestedState extends IState {
  children?: INestedState[];
}

export class StateConfigProvider implements ng.IServiceProvider {

  private root: INestedState = {
    name: 'home',
    abstract: true,
    children: [],
  };

  static get $inject() { return ['$stateProvider', '$urlRouterProvider', 'stateHelperProvider']; }

  constructor(private $stateProvider: IStateProvider,
              private $urlRouterProvider: IUrlRouterProvider,
              private stateHelperProvider: StateHelper) {}

  /**
   * Adds a root state, e.g. /applications, /projects, /infrastructure
   * @param child the state to add
   */
  public addToRootState(child: INestedState): void {
    const current = this.root.children.find(c => c.name === child.name);
    if (!current) {
      this.root.children.push(child);
    }
    this.setStates();
  }

  /**
   * registers any states that have been added as children to an already-registered state
   * (really just called internally by #addToRootState and by the ApplicationStateProvider methods)
   */
  public setStates(): void {
    this.stateHelperProvider.setNestedState(this.root);
  }

  /**
   * Configures a rewrite rule to automatically replace a base route
   * @param base, e.g. "/applications/{application}"
   * @param replacement, e.g. "/applications/{application}/clusters"
   */
  public addRewriteRule(base: string, replacement: string) {
    this.$urlRouterProvider.when(base, replacement);
  }

  public $get(): StateConfigProvider {
    return this;
  }
}

export const STATE_CONFIG_PROVIDER = 'spinnaker.core.navigation.state.config.provider';
module(STATE_CONFIG_PROVIDER, [
  require('angular-ui-router'),
  STATE_HELPER,
]).provider('stateConfig', StateConfigProvider)
  .config(($urlRouterProvider: IUrlRouterProvider) => {
    $urlRouterProvider.otherwise('/');
    // Don't crash on trailing slashes
    $urlRouterProvider.when('/{path:.*}/', ['$match', ($match: any) => {
      return '/' + $match.path;
    }]);
  });
