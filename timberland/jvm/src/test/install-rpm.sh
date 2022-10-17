#!/usr/bin/env bash
set -xu

copy_logs() {
#  set +e
  mkdir /home/centos/nomad-logs
  rsync -av --relative /opt/radix/nomad/alloc/./* /home/centos/nomad-logs
  chmod -R 777 /home/centos/nomad-logs
  journalctl -u consul > /tmp/consul.log
  journalctl -u nomad > /tmp/nomad.log
  journalctl -u vault > /tmp/vault.log
  journalctl -u consul-template > /tmp/consul-template.log
#  set -e
}

trap copy_logs EXIT

docker swarm init

yum install -y epel-release
yum install -y jq
yum install -y psmisc

yum install -y ./timberland-rpm-all.rpm

cd /opt/radix/timberland/exec
./timberland enable all
./timberland disable elemental
./timberland disable runtime
./timberland disable algs
./timberland disable utils
./timberland disable device_drivers
./timberland disable tui
./timberland disable retool
./timberland disable elasticsearch
./timberland start

TIMBERLAND_EXIT_CODE=$?
echo "Timberland exit code: $TIMBERLAND_EXIT_CODE"

sleep 3

chmod +x /home/centos/gather-results.sh
/home/centos/gather-results.sh | tee /tmp/results.log
/home/centos/service_test.py | tee /tmp/service_test.log

TEST_EXIT_CODE=${PIPESTATUS[0]}
echo "Service test exit code: $TEST_EXIT_CODE"

if [ "$TIMBERLAND_EXIT_CODE" -eq 0 ] && [ "$TEST_EXIT_CODE" -eq 0 ]; then
  EXIT_CODE=0
else
  EXIT_CODE=1
fi

exit $EXIT_CODE
