echo "Connecting to... "
echo $DATA_CASSANDRA_SERVICE_HOST
echo $DATA_CASSANDRA_SERVICE_PORT

FILES=`ls -1 keys/`
for f in $FILES; do
    cqlsh $DATA_CASSANDRA_SERVICE_HOST $DATA_CASSANDRA_SERVICE_PORT -f keys/$f;
    if [ $? -eq 0 ]; then
        echo "Added keyspace $f"
    else
        echo "Failed to add keyspace $f"
        exit 17
    fi
done

sleep 100000
