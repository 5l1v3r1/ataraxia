#!/usr/bin/sh

export PATH="/usr/local/usr/bin:/usr/local/usr/bin:/usr/usr/bin"

dmesg -n 1

clear

mountpoint -q /proc || mount -o nosuid,noexec,nodev -t proc proc /proc
mountpoint -q /sys || mount -o nosuid,noexec,nodev -t sysfs sys /sys
mountpoint -q /run || mount -o mode=0755,nosuid,nodev -t tmpfs run /run
mountpoint -q /dev || mount -o mode=0755,nosuid -t devtmpfs dev /dev
mkdir -p -m0755 /run/lvm /run/user /run/lock /run/log /dev/pts /dev/shm
mountpoint -q /dev/pts || mount -o mode=0620,gid=5,nosuid,noexec -n -t devpts devpts /dev/pts
mountpoint -q /dev/shm || mount -o mode=1777,nosuid,nodev -n -t tmpfs shm /dev/shm
mountpoint -q /sys/kernel/security || mount -n -t securityfs securityfs /sys/kernel/security
mountpoint -q /sys/fs/cgroup || mount -o mode=0755 -t tmpfs cgroup /sys/fs/cgroup

for f in $(kmod static-nodes 2>/dev/null|awk '/Module/ {print $2}'); do
	modprobe -bq $f 2>/dev/null
done

modules-load -v | tr '\n' ' ' | sed 's:insmod [^ ]*/::g; s:\.ko\(\.xz\)\? ::g'

udevd --daemon
udevadm trigger --action=add --type=subsystems
udevadm trigger --action=add --type=devices
udevadm trigger --action=change --type=devices
udevadm settle

if [[ -f '/etc/font.conf' ]]
then
	export FONT="$(cat /etc/font.conf)"
	setfont $FONT
else
	setfont default
fi

if [[ -f '/etc/kmap.conf' ]]
then
	export KEYMAP="$(cat /etc/kmap.conf)"
	loadkeys -q $KEYMAP
else
	loadkeys -q us
fi

hwclock --hctosys --utc

mount -o remount,ro /

if [ -x /usr/bin/dmraid -o -x /usr/bin/dmraid ]; then
	dmraid -i -ay
fi

if [ -x /usr/bin/btrfs ]; then
	btrfs device scan || exec sh
fi

if [ -x /usr/bin/vgchange -o -x /usr/bin/vgchange ]; then
	vgchange --sysinit -a y || exec sh
fi

if [ -f /forcefsck ]; then
FORCEFSCK="-f"
fi

fsck $FORCEFSCK -A -T -C -a
if [ $? -gt 1 ]; then
	echo
	echo "***************  FILESYSTEM CHECK FAILED  ******************"
	echo "*                                                          *"
	echo "*  Please repair manually and reboot. Note that the root   *"
	echo "*  file system is currently mounted read-only. To remount  *"
	echo "*  it read-write type: mount -n -o remount,rw /            *"
	echo "*  When you exit the maintainance shell the system will    *"
	echo "*  reboot automatically.                                   *"
	echo "*                                                          *"
	echo "************************************************************"
	echo
	sulogin -p
	umount -a -r
	mount -o remount,ro /
	reboot -f
	exit 0
fi

mount -o remount,rw /

swapon -a

mount -a -t "nosysfs,nonfs,nonfs4,nosmbfs,nocifs" -O no_netdev

cp /var/lib/random-seed /dev/urandom >/dev/null 2>&1 || true
( umask 077; bytes=$(cat /proc/sys/kernel/random/poolsize) || bytes=512; dd if=/dev/urandom of=/var/lib/random-seed count=1 bs=$bytes >/dev/null 2>&1 )

ip link set up dev lo

if [[ -f '/etc/hostname' ]]
then
	hostname -F /etc/hostname
else
	hostname miyuki
fi

if [[ -f '/etc/timezone' ]]
then
	MYTZ="$(cat /etc/timezone)"
	ln -sf /usr/share/zoneinfo/$MYTZ /etc/localtime
else
	ln -sf /usr/share/zoneinfo/Europe/Moscow /etc/localtime
fi

sysctl -q --system

install -m0664 -o root -g utmp /dev/null /run/utmp
if [ ! -e /var/log/wtmp ]; then
	install -m0664 -o root -g utmp /dev/null /var/log/wtmp
fi
if [ ! -e /var/log/btmp ]; then
	install -m0600 -o root -g utmp /dev/null /var/log/btmp
fi
install -dm1777 /tmp/.X11-unix /tmp/.ICE-unix
rm -f /etc/nologin /forcefsck /forcequotacheck /fastboot

dmesg >/var/log/dmesg.log

: > /run/utmp

echo
/usr/bin/sh -c '/usr/bin/respawn /usr/bin/agetty 38400 tty1 linux' &>/dev/null & || exec /usr/bin/mksh