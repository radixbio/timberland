#!/usr/bin/env bash
set -exu

docker swarm init

until dpkg -i radix-timberland_0.1_amd64.deb
do
  echo "waiting for dpkg..."
  sleep 2
done

cd /opt/radix/timberland/exec
docker network create --attachable -d weaveworks/net-plugin:2.6.0 weave  --ip-range 10.32.0.0/12 --subnet 10.32.0.0/12

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

/home/ubuntu/service_test.py | tee /tmp/service_test.log

TEST_EXIT_CODE=$?
echo "Service test exit code: $TEST_EXIT_CODE"

mkdir /home/ubuntu/nomad-logs

rsync -av --relative /opt/radix/nomad/alloc/./*/alloc/logs/* /home/ubuntu/nomad-logs

exit $TEST_EXIT_CODE
