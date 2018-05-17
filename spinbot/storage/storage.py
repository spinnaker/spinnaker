import logging

class Storage(object):
    def __init__(self):
        self.id = self._id()
        self.logging = logging.getLogger(self.id)

    def _id(self):
        name = self.__class__.__name__
        name = ''.join(map(lambda c: '_' + c.lower() if c.isupper() else c, name)).strip('_')
        return name

    def store(self, val, key):
        raise NotImplementedError("store not implemented")

    def load(self, val):
        raise NotImplementedError("load not implemented")
