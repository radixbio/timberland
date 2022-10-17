# Installing Timberland

For more info, go to the
[Notion Page](https://www.notion.so/radixlabs/Timberland-e910eaf3bab74430bc2958b8b8d51bf5#fc4e0425e683418984789f3ca1c48ad2)
describing Timberland installation.

## Installing/Uninstalling From a 0install Package

First, 0install must be installed on the target computer:

```bash
sudo apt install 0install
```

The 0install build rule produces a .tar file which includes everything you need to install timberland.

Extract the archive, then from within that directory (the one that contains `timberland.xml`) do

```bash
sudo 0install run ./timberland.xml
```

Add `/opt/radix/timberland/exec` to your $PATH.

To uninstall, from the same directory run:

```bash
sudo 0install --command uninstall run ./timberland.xml
```

