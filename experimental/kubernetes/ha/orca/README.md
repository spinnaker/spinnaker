# Orca

Orca is Spinnaker's orchestration engine, meaning it handles the execution of
all Spinnaker pipelines. Orca can be scaled horizontally as you see fit;
however, it is not safe to kill instances of orca indiscriminately as there is
currently no work-sharing built in to orca to migrate one running pipeline from
one instance to another. The update strategy will be detailed below.
