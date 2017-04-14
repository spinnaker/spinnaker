
import {PropertyCommand} from './propertyCommand.model';
import {IUser} from 'core/authentication/authentication.service';
import {PropertyCommandType} from './propertyCommandType.enum';
import {ICanaryDeployment} from './canaryDeployment.interface';
import {AcaTaskStageConfigDetails} from './acaTaskStageConfigDetails.model';

class CanaryAnalysisConfig {
  public notificationHours: number[] = [];
  public useLookback: boolean;
  public lookbackMins: number;
  public name: string;
  public beginCanaryAnalysisAfterMins: string;
  public canaryAnalysisIntervalMins: string;

  constructor(config: any) {
    this.notificationHours = config.notificationHours.split(',').map((num: string) => parseInt(num, 10));
    this.useLookback = config.useLookback;
    this.lookbackMins = config.lookbackMins;
    this.name = config.name;
    this.beginCanaryAnalysisAfterMins = config.beginCanaryAnalysisAfterMins;
    this.canaryAnalysisIntervalMins = config.canaryAnalysisIntervalMins;
  }
}

class CanarySuccessCriteria {
  constructor(public canaryResultScore: string) {}
}

class CanaryHealthCheckHandler {
  public '@class' = 'com.netflix.spinnaker.mine.CanaryResultHealthCheckHandler';
  constructor(public minimumCanaryResultScore: string) {}
}

class CanaryConfig {
  public name: string;
  public lifetimeHours: string;
  public combinedCanaryResultStrategy: string;
  public canaryHealthCheckHandler: CanaryHealthCheckHandler;
  public canaryAnalysisConfig: CanaryAnalysisConfig;
  public canarySuccessCriteria: CanarySuccessCriteria;

  constructor(command: PropertyCommand) {
    this.name = command.strategy.configDetails.name || `FP - ${PropertyCommandType[command.type]} ACA Task`;
    command.strategy.configDetails.name = this.name;
    this.lifetimeHours = command.strategy.configDetails.lifetimeHours;
    this.combinedCanaryResultStrategy = command.strategy.configDetails.combinedCanaryResultStrategy;
    this.canaryHealthCheckHandler = new CanaryHealthCheckHandler(command.strategy.configDetails.minimumCanaryResultScore);
    this.canaryAnalysisConfig = new CanaryAnalysisConfig(command.strategy.configDetails);
    this.canarySuccessCriteria = new CanarySuccessCriteria(command.strategy.configDetails.canaryResultScore);
  }
}

class CanaryDeployments {
  public '@class' = '.CanaryTaskDeployment';

  public region: string;
  public accountName: string;
  public baseline: string;
  public canary: string;
  public type: string;

  constructor(canaryDeployment: ICanaryDeployment) {
    this.region = canaryDeployment.region;
    this.accountName = canaryDeployment.accountName;
    this.baseline = canaryDeployment.baseline;
    this.canary = canaryDeployment.canary;
    this.type = canaryDeployment.type;
  }
}

export class Canary {
  public application = 'spinnakerfp';
  public owner: string;
  public canaryConfig: CanaryConfig;
  public watchers: string[] = [];
  public canaryDeployments: CanaryDeployments[];

  constructor(user: IUser, command: PropertyCommand, configDetails: AcaTaskStageConfigDetails ) {
    this.owner = user.name;
    this.canaryConfig = new CanaryConfig(command);
    this.watchers = configDetails.watchers.split(',');
    this.canaryDeployments = [new CanaryDeployments(command.strategy.configDetails)];
  }
}
