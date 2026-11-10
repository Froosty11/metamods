R4 scratch: NametagScratchClientTest.java was copied to mods/ovvar/src/gametest/java/se/metacraft/ovvar/clienttest/ and listed FIRST in
src/gametest/resources/fabric.mod.json (gated on /tmp/nametag.on); build.gradle got a doFirst (copy chapter-text-colors.zip to the run dir's
resourcepacks, write options.txt resourcePacks:["vanilla","file/chapter-text-colors.zip"]) and a -PnametagCtl variant outside the iris block. Reverted afterwards.
Other files are measuring helpers. BSL = BSL_v10.1.8.zip (Modrinth bsl-shaders, latest for 26.3).
