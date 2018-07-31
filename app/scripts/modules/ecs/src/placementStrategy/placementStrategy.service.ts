import { module } from 'angular';

import { IPlacementStrategy } from './IPlacementStrategy';

export class PlacementStrategyService {
  public getPredefinedStrategy(strategyName: string): IPlacementStrategy[] {
    if (strategyName === 'AZ Balanced Spread') {
      return this.getAzBalancedSpreadStrategy();
    } else if (strategyName === 'AZ Balanced BinPack') {
      return this.getAzBalancedBinPackStrategy();
    } else if (strategyName === 'BinPack') {
      return this.getBinPackStrategy();
    } else if (strategyName === 'One Task Per Host') {
      return this.getOneTaskPerHostStrategy();
    } else {
      // TODO: Add support for custom placement strategy.
      return [];
    }
  }

  public getAzBalancedSpreadStrategy(): IPlacementStrategy[] {
    return [{ type: 'spread', field: 'attribute:ecs.availability-zone' }, { type: 'spread', field: 'instanceId' }];
  }

  public getAzBalancedBinPackStrategy(): IPlacementStrategy[] {
    return [{ type: 'spread', field: 'attribute:ecs.availability-zone' }, { type: 'binpack', field: 'memory' }];
  }

  public getBinPackStrategy(): IPlacementStrategy[] {
    return [{ type: 'binpack', field: 'memory' }];
  }

  public getOneTaskPerHostStrategy(): IPlacementStrategy[] {
    return [{ type: 'spread', field: 'instanceId' }];
  }
}

export const PLACEMENT_STRATEGY_SERVICE = 'spinnaker.ecs.placementStrategyService.service';

module(PLACEMENT_STRATEGY_SERVICE, []).service('placementStrategyService', PlacementStrategyService);
