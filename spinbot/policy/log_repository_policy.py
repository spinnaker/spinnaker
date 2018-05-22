from gh import ObjectType
from .policy import Policy

class LogRepositoryPolicy(Policy):
    def __init__(self):
        super().__init__()

    def applies(self, o):
        return ObjectType(o) == 'repository'

    def apply(self, g, o):
        repo = o.full_name
        stars = o.stargazers_count
        forks = o.forks_count
        size = o.size
        watchers = o.watchers_count
        contributors = len(list(o.get_contributors()))
        self.monitoring_db.write('repo', {
            'stars': stars,
            'forks': forks,
            'size': size,
            'watchers': watchers,
            'contributors': contributors
        }, tags={
            'repo': repo
        })

LogRepositoryPolicy()
