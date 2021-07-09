import { StateParams } from '@uirouter/angularjs';
import { IServiceProvider, module } from 'angular';

import { ApplicationComponent } from './ApplicationComponent';
import { Application } from './application.model';
import { ApplicationModelBuilder } from './applicationModel.builder';
import { InsightLayout } from '../insight/InsightLayout';
import { INestedState, STATE_CONFIG_PROVIDER, StateConfigProvider } from '../navigation/state.provider';
import { ApplicationReader } from './service/ApplicationReader';
import { InferredApplicationWarningService } from './service/InferredApplicationWarningService';

export class ApplicationStateProvider implements IServiceProvider {
  private childStates: INestedState[] = [];
  private detailStates: INestedState[] = [];
  private insightStates: INestedState[] = [];
  private insightState: INestedState = {
    name: 'insight',
    abstract: true,
    views: {
      insight: {
        component: InsightLayout,
        $type: 'react',
      },
    },
    resolve: {
      app: ['app', (app: Application) => app],
    },
    children: this.insightStates,
  };

  public static $inject = ['stateConfigProvider'];
  constructor(private stateConfigProvider: StateConfigProvider) {
    this.childStates.push(this.insightState);
  }

  /**
   * Adds a direct child to the application that does not use the Insight (i.e. inspector) views, e.g. tasks
   * @param state
   */
  public addChildState(state: INestedState): void {
    this.childStates.push(state);
    this.stateConfigProvider.setStates();
  }

  /**
   * Adds a view that includes the nav, master, and detail sections, e.g. clusters
   * @param state
   */
  public addInsightState(state: INestedState): void {
    this.insightStates.push(state);
    state.children = this.detailStates;
    this.stateConfigProvider.setStates();
  }

  /**
   * Adds an inspector view to all insight states. Adding an insight detail state makes that view available to all
   * parent insight views, so, for example, adding the load balancer details state makes it available to cluster,
   * firewall, and load balancer insight parent states
   * @param state
   */
  public addInsightDetailState(state: INestedState): void {
    this.detailStates.push(state);
    this.insightState.children.forEach((c) => {
      c.children = c.children || [];
      if (!c.children.some((child) => child.name === state.name)) {
        c.children.push(state);
      }
    });
    this.stateConfigProvider.setStates();
  }

  /**
   * Configures the application as a child view of the provided parent
   * @param parentState
   * @param mainView the ui-view container for the application
   * @param relativeUrl (optional) the prefix used for the application view
   */
  public addParentState(parentState: INestedState, mainView: string, relativeUrl = '') {
    const applicationConfig: INestedState = {
      name: 'application',
      url: `${relativeUrl}/:application`,
      redirectTo: (transition) => {
        return transition
          .injector()
          .getAsync('app')
          .then((app: Application) => {
            const defaultDataSource = app.dataSources.find((ds) => ds.sref && !ds.disabled)?.sref;

            const params = transition.params();
            // If there's no data source to route to, we need to use the absolute href 'home.search'
            const options = { relative: defaultDataSource ? transition.to().name : undefined };

            return transition.router.stateService.target(defaultDataSource || 'home.search', params, options);
          });
      },
      resolve: {
        app: [
          '$stateParams',
          ($stateParams: StateParams) => {
            return ApplicationReader.getApplication($stateParams.application, false)
              .then(
                (app: Application): Application => {
                  InferredApplicationWarningService.checkIfInferredAndWarn(app);
                  return app || ApplicationModelBuilder.createNotFoundApplication($stateParams.application);
                },
              )
              .catch((error) => {
                if (error.status && error.status === 404) {
                  return ApplicationModelBuilder.createNotFoundApplication($stateParams.application);
                } else {
                  // tslint:disable-next-line:no-console
                  console.error(error);
                  return ApplicationModelBuilder.createApplicationWithError($stateParams.application);
                }
              });
          },
        ],
      },
      data: {
        pageTitleMain: {
          field: 'application',
        },
        history: {
          type: 'applications',
          state: 'home.applications.application',
          keyParams: ['application'],
        },
      },
      children: this.childStates,
    };
    applicationConfig.views = {};
    applicationConfig.views[mainView] = {
      component: ApplicationComponent,
      $type: 'react',
    };
    parentState.children.push(applicationConfig);
    this.stateConfigProvider.setStates();
  }

  public $get() {
    return this;
  }
}

export const APPLICATION_STATE_PROVIDER = 'spinnaker.core.application.state.provider';
module(APPLICATION_STATE_PROVIDER, [STATE_CONFIG_PROVIDER]).provider('applicationState', ApplicationStateProvider);
