import logging
import monitoring
from .policy_registry import RegisterPolicy, GetPolicyConfig

class Policy(object):
    def __init__(self):
        self.id = self._id()
        self.logging = logging.getLogger(self.id)
        self.monitoring_db = monitoring.GetDatabase('spinbot')
        self.config = GetPolicyConfig(self.id)
        RegisterPolicy(self)

    def _id(self):
        name = self.__class__.__name__
        name = ''.join(map(lambda c: '_' + c.lower() if c.isupper() else c, name)).strip('_')
        return name

    def applies(self, o):
        raise NotImplementedError('applies not implemented')

    def apply(self, g, o):
        raise NotImplementedError('apply not implemented')

