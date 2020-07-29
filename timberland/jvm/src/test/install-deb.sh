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
./timberland runtime start

EXIT_CODE=$?
echo "Timberland test exit code: $EXIT_CODE"

sleep 3

/home/ubuntu/service_test.py | tee /tmp/service_test.log

echo "Service test exit code: $?"

mkdir /home/ubuntu/nomad-logs

rsync -av --relative /opt/radix/nomad/alloc/./*/alloc/logs/* /home/ubuntu/nomad-logs

exit $EXIT_CODE
