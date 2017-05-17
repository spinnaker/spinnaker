import { IComponentController, IComponentOptions, IScope, module } from 'angular';

import { ACCOUNT_SERVICE, AccountService, IAccount } from '@spinnaker/core';

import { PropertyCommand } from '../../domain/propertyCommand.model';
import { PropertyCommandType } from '../../domain/propertyCommandType.enum';
import { PropertyPipeline } from '../../domain/propertyPipeline.domain';
import { AcaStrategy, ForcePushStrategy, ManualStrategy } from '../../domain/propertyStrategy.domain';
import {
  FAST_PROPERTY_PIPELINE_BUILDER_SERVICE,
  PropertyPipelineBuilderService
} from '../propertyPipelineBuilder.service';

import './propertyStrategy.component.less';

export class FastPropertyStrategyComponentController implements IComponentController {
  public command: PropertyCommand;
  public pipeline: PropertyPipeline;
  public recommendationText: string;
  public accounts: IAccount[];
  public regions: any;

  constructor(private $scope: IScope,
              private accountService: AccountService,
              private propertyPipelineBuilderService: PropertyPipelineBuilderService) {
    'ngInject';

    this.accountService.listAccounts('aws').then( (accounts: IAccount[]) => {
      this.accounts = accounts;
    });

    accountService.getUniqueAttributeForAllAccounts('aws', 'regions')
      .then( (regions: any) => {
        this.regions = regions;
      });
  }

  public $onInit() {
    this.$scope.$watchCollection('$ctrl.command.property', () => this.buildPropertyPipeline(this.command));
    this.$scope.$watchCollection('$ctrl.command.scopes', () => this.suggestStrategyAndBuildPipeline(this.command));
    this.$scope.$watchCollection('$ctrl.command.strategy', () => this.buildPropertyPipeline(this.command));
    this.$scope.$watchCollection('$ctrl.command.strategy.configDetails', () => this.buildPropertyPipeline(this.command));
  }

  public selectManual() {
    this.command.strategy = new ManualStrategy(require('./manualStrategyForm.html'));
  }

  public selectAca() {
    this.command.strategy = new AcaStrategy(require('./acaStrategyForm.html'));
    this.command.accounts = this.accounts;
    this.command.regions = this.regions;
  }

  public selectForcePush() {
    this.command.strategy = new ForcePushStrategy(require('./forcePushStrategyForm.html'));
  }

  private buildPropertyPipeline(command: PropertyCommand) {
    if (command && command.scopes && command.property && command.isReadyForPipeline()) {
      this.propertyPipelineBuilderService.build(command)
        .then((pipeline: PropertyPipeline) => {
          this.pipeline = pipeline;
          command.pipeline = this.pipeline;
        });
    }
  }

  private suggestStrategyAndBuildPipeline(command: PropertyCommand) {
    if (command.isReadyForStrategy()) {
      if (command.type === PropertyCommandType.CREATE) {
        this.selectForcePush();
        this.recommendationText = `The "Force Push" strategy is recommended for creating a new Fast Property that only affects ${command.getCombinedInstanceCountsForAllScopes()} running instances`;
      }
      if (command.type === PropertyCommandType.UPDATE) {
        this.selectManual();
        this.recommendationText = `The "ACA" strategy is recommended for updating a Fast Property that affects ${command.getCombinedInstanceCountsForAllScopes()} running instances`;
      }
      if (command.type === PropertyCommandType.DELETE) {
        this.selectManual();
        this.recommendationText = `The "Manual" strategy is recommended for deleting a Fast Property that affects ${command.getCombinedInstanceCountsForAllScopes()} running instances`;
      }
    }
  }
}

class FastPropertyStrategyComponent implements IComponentOptions {
  public templateUrl: string = require('./propertyStrategy.component.html');
  public controller: any = FastPropertyStrategyComponentController;
  public bindings: any = {
    command: '='
  };
}

export const FAST_PROPERTY_STRATEGY_COMPONENT = 'spinnaker.netflix.fastProperty.strategy.component';

module(FAST_PROPERTY_STRATEGY_COMPONENT, [
  ACCOUNT_SERVICE,
  FAST_PROPERTY_PIPELINE_BUILDER_SERVICE,
])
  .component('fastPropertyStrategy', new FastPropertyStrategyComponent());
