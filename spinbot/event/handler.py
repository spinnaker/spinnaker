import logging
import monitoring
from os.path import basename, realpath
from .handler_registry import RegisterHandler, GetHandlerConfig

class Handler(object):
    def __init__(self):
        self.id = self._id()
        self.logging = logging.getLogger(self.id)
        self.monitoring_db = monitoring.GetDatabase('spinbot')
        self.config = GetHandlerConfig(self.id)
        RegisterHandler(self)

    def _id(self):
        name = self.__class__.__name__
        name = ''.join(map(lambda c: '_' + c.lower() if c.isupper() else c, name)).strip('_')
        return name

    def handles(self, event):
        raise NotImplementedError('handles not implemented')

    def handle(self, g, event):
        raise NotImplementedError('handle not implemented')
