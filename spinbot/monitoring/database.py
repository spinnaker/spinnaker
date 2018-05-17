import logging
from datetime import datetime

class Database(object):
    def __init__(self, database):
        self.id = self._id()
        self.logging = logging.getLogger(self.id)
        self.database = database
        self.points = []

    def _id(self):
        name = self.__class__.__name__
        name = ''.join(map(lambda c: '_' + c.lower() if c.isupper() else c, name)).strip('_')
        return name

    def write_all_points(self, align_points=False):
        self._write_all_points(self.points, align_points=align_points)

    def now(self):
        return datetime.utcnow().strftime(self.time_format())

    def write(self, name, fields, tags={}):
        self.points.append(self._point(name, fields, tags=tags))

    def time_format(self):
        raise NotImplementedError("__time_format not implemented")
