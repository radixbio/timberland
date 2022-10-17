if [ -f /opt/radix/timberland/exec/timberland ]; then
    cd /opt/radix/timberland/exec/
    ./timberland dns down
fi
systemctl stop consul nomad vault timberland-svc || true
