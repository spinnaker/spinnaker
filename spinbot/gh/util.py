import github

def IssueRepo(issue):
    return '/'.join(issue.url.split('/')[-4:-2])

def HasLabel(issue, name):
    label = next((l for l in issue.get_labels() if l.name == name), None)
    return label is not None

def AddLabel(gh, issue, name):
    label = gh.get_label_or_create(IssueRepo(issue), name) 
    issue.add_to_labels(label)

def ObjectType(o):
    if isinstance(o, github.Issue.Issue):
        return 'issue'
    else:
        return None
