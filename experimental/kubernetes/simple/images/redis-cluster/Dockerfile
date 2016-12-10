FROM redis:2.8

COPY redis-master.conf /redis-master/redis.conf
COPY redis-slave.conf /redis-slave/redis.conf
ADD run.sh .

CMD [ "./run.sh" ]
ENTRYPOINT [ "bash", "-c" ]
