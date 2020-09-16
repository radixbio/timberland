#!/usr/bin/env bash

docker swarm init
yes | docker plugin install weaveworks/net-plugin:2.6.0
docker plugin disable weaveworks/net-plugin:2.6.0
docker plugin set weaveworks/net-plugin:2.6.0 IPALLOC_RANGE=10.32.0.0/12
docker plugin enable weaveworks/net-plugin:2.6.0

yum install -y psmisc
yum install -y ./timberland-rpm-all.rpm

cd /opt/radix/timberland/exec
docker network create --attachable -d weaveworks/net-plugin:2.6.0 weave
./timberland runtime enable all
./timberland runtime disable elemental
./timberland runtime disable algs
./timberland runtime disable utils
./timberland runtime disable device_drivers
./timberland runtime disable tui
./timberland runtime start

TIMBERLAND_EXIT_CODE=$?
echo "Timberland exit code: $TIMBERLAND_EXIT_CODE"

sleep 3

/home/centos/service_test.py | tee /tmp/service_test.log

TEST_EXIT_CODE=$?
echo "Service test exit code: $TEST_EXIT_CODE"

mkdir /home/centos/nomad-logs
rsync -av --relative /opt/radix/nomad/alloc/./*/alloc/logs/* /home/centos/nomad-logs

exit $TEST_EXIT_CODE
