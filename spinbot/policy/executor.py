import policy
import traceback
import logging
import monitoring
import itertools
from .policy_registry import GetConfig

def ApplyPolicies(g):
    config = GetConfig()
    enabled = config.get('enabled', True)
    if enabled is not None and not enabled:
        return

    monitoring_db = monitoring.GetDatabase('spinbot')

    logging.info('Processing issues, repos')
    for i in itertools.chain(*[g.issues(), g.pull_requests(), g.repos()]):
        for p in policy.Policies():
            if p.applies(i):
                err = None
                try:
                    p.apply(g, i)
                except Exception as _err:
                    logging.warn('Failure applying {} to {}: {}'.format(
                            p, i, traceback.format_exc()
                    ))
                    err = _err

                monitoring_db.write('issues_handled', { 'value': 1 }, tags={
                    'policy': p.id,
                    'error': err
                })
