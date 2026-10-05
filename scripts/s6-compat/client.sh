#!/usr/bin/env bash
# client.sh NAME PORT [DISPLAY] — headless vanilla 26.3 client (Xvfb + Mesa llvmpipe) that quick-plays into localhost:PORT.
ROOT=${ROOT:-/home/user/s6}; JAVA=${JAVA:-$(ls -d /opt/jdk/jdk-25*)/bin/java}
name=$1; port=$2; disp=${3:-:99}; gd=$ROOT/clients/$name; mkdir -p $gd
pgrep -f "Xvfb $disp" >/dev/null || { Xvfb $disp -screen 0 1280x720x24 >/dev/null 2>&1 & sleep 2; }
cd $ROOT
DISPLAY=$disp LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe exec $JAVA -Xmx3G \
  -Djava.library.path=$gd/natives -Dorg.lwjgl.util.Debug=false \
  -cp "$(cat client/classpath.txt)" net.minecraft.client.main.Main \
  --username $name --uuid $(python3 -c "import uuid;print(uuid.uuid3(uuid.NAMESPACE_DNS,'$name'))") \
  --accessToken 0 --userType msa --version 26.3 --gameDir $gd \
  --assetsDir $ROOT/client/assets --assetIndex 34 --quickPlayMultiplayer 127.0.0.1:$port
