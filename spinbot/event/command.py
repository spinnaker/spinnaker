def GetCommands(text):
    if not text:
        return None

    lines = text.splitlines()
    for line in lines:
        line = line.strip()
        # todo(lwander): parameterize
        if line.startswith('@spinnakerbot'):
            command = line[len('@spinnakerbot'):].strip()
            if len(command) > 0:
                yield command.split()
