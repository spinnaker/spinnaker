FILES=`ls -1 keys/`
for f in $FILES; do
    cqlsh $SPKR_CASSANDRA_SERVICE_HOST $SPKR_CASSANDRA_SERVICE_PORT -f keys/$f;
    if [ $? -eq 0 ]; then
        echo "Added keyspace $f"
    else
        echo "Failed to add keyspace $f"
        exit 17
    fi
done
