import { strategyBlueGreen } from './bluegreen.strategy';
import { strategyHighlander } from './highlander.strategy';
import { strategyNone } from './none.strategy';
import { strategyRedBlack } from './redblack.strategy';

export const rolloutStrategies = [strategyNone, strategyRedBlack, strategyHighlander, strategyBlueGreen];
