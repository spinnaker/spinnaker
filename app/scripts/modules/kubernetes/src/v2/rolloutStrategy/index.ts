import { strategyHighlander } from 'kubernetes/v2/rolloutStrategy/highlander.strategy';
import { strategyNone } from 'kubernetes/v2/rolloutStrategy/none.strategy';
import { strategyRedBlack } from 'kubernetes/v2/rolloutStrategy/redblack.strategy';

export const rolloutStrategies = [strategyNone, strategyRedBlack, strategyHighlander];
