from .database import Database

class NoopDatabase(Database):
    def __init__(self, database):
        self.id = 'NoopDatabase'
        super().__init__(database)

    def _write_all_points(self, points, align_points=False):
        pass

    def _point(self, name, fields, tags={}):
        pass

    def time_format(self):
        return ''
