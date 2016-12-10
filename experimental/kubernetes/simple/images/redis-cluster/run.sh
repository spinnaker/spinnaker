function launchmaster() {
    if [[ ! -e /redis-master-data ]]; then
        mkdir /redis-master-data
    fi

    redis-server /redis-master/redis.conf
}

function launchslave() {
    if [[ ! -e /redis-slave-data ]]; then
        mkdir /redis-slave-data
    fi

    perl -pi -e "s/%component%/${COMPONENT}/" /redis-slave/redis.conf

    cat /redis-slave/redis.conf

    redis-server /redis-slave/redis.conf
}


if [[ "${MASTER}" == "true" ]]; then
    echo "Running redis in master configuration."
    launchmaster
    exit 0
fi

if [[ "${SLAVE}" == "true" ]]; then
    echo "Running redis in slave configuration."
    launchslave
    exit 0
fi

echo "Neither slave nor master configuration selected."
exit 2
