from gh import ObjectType
from datetime import datetime
from .policy import Policy

class LogIssuePolicy(Policy):
    def __init__(self):
        super().__init__()

    def applies(self, o):
        return ObjectType(o) == 'issue'

    def apply(self, g, o):
        now = datetime.now()
        delta = now - o.created_at
        repo = '/'.join(o.url.split('/')[-4:-2])
        self.monitoring_db.write('issue', { 
            'days_since_created': delta.days,
            'count': 1
        }, tags={ 
            'repo': repo, 
            'user': o.user.login 
        })

LogIssuePolicy()
