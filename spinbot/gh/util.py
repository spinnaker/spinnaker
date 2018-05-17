import github

def ObjectType(o):
    if isinstance(o, github.Issue.Issue):
        return 'issue'
    else:
        return None
