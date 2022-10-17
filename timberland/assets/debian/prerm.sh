if [ -f /opt/radix/timberland/exec/timberland ]; then
    cd /opt/radix/timberland/exec/
    ./timberland runtime dns down
fi
systemctl stop consul nomad vault
