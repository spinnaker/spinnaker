import './propertyStrategy.component.less';
import { module } from 'angular';
import { FAST_PROPERTY_PIPELINE_BUILDER_SERVICE, PropertyPipelineBuilderService} from '../propertyPipelineBuilder.service';
import {PropertyCommand} from '../../domain/propertyCommand.model';
import {PropertyPipeline} from '../../domain/propertyPipeline.domain';
import {PropertyCommandType} from '../../domain/propertyCommandType.enum';
import {ForcePushStrategy, ManualStrategy, AcaStrategy} from '../../domain/propertyStrategy.domain';
import {ACCOUNT_SERVICE, AccountService, IAccount } from 'core/account/account.service';

export class FastPropertyStrategyComponentController implements ng.IComponentController {
  public command: PropertyCommand;
  public pipeline: PropertyPipeline;
  public recommendationText: string;
  public accounts: IAccount[];
  public regions: any;


  static get $inject() {
    return [
      '$scope',
      'accountService',
      'propertyPipelineBuilderService',
    ];
  }

  constructor(private $scope: ng.IScope,
              private accountService: AccountService,
              private propertyPipelineBuilderService: PropertyPipelineBuilderService) {

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
    this.$scope.$watchCollection('$ctrl.command.scope', () => this.suggestStrategyAndBuildPipeline(this.command));
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
    if (command && command.scope && command.property && command.isReadyForPipeline()) {
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
        this.recommendationText = `The "Force Push" strategy is recommended for creating a new Fast Property that only affects ${command.scope.instanceCounts.up} running instances`;
      }
      if (command.type === PropertyCommandType.UPDATE) {
        this.selectAca();
        this.recommendationText = `The "ACA" strategy is recommended for updating a Fast Property that affects ${command.scope.instanceCounts.up} running instances`;
      }
      if (command.type === PropertyCommandType.DELETE) {
        this.selectManual();
        this.recommendationText = `The "Manual" strategy is recommended for deleting a Fast Property that affects ${command.scope.instanceCounts.up} running instances`;
      }
    }
  }
}

class FastPropertyStrategyComponent implements ng.IComponentOptions {
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
