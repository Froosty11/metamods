# Mod sets for the S6 compatibility check. Source this file; names are globs under $JARS.
B0="fabric-api-* c2me-fabric-* DistantHorizons-* spark-* Chunky-Fabric-*"
S6_COMMON="$B0 metacraft-dist.jar danse-* polydecorations-* polymer-bundled-* alternate-current-* lithium-fabric-*
 ferritecore-* krypton-* ScalableLux-* NoChatReports-* ledger-* fabric-language-kotlin-* vanish-* LuckPerms-*
 voicechat-* squaremap-* worldedit-* filament-* booklet-* fabrictailor-* forgivingvoid-* balm-* meowantixray-*"
HEAVY="Terralith_* tectonic-* lithostitched-* dungeons-and-taverns-* Incendium_* Nullscape_*"
modset() {
  case "$1" in
    none) echo "" ;;
    b0) echo "$B0" ;;
    s6v) echo "$S6_COMMON" ;;
    s6h) echo "$S6_COMMON $HEAVY" ;;
    s6v-carpet) echo "$S6_COMMON fabric-carpet-*" ;;
    s6v-noac) echo "$S6_COMMON" | sed 's/alternate-current-\*//' ;;
    s6v-bluemap) echo "$S6_COMMON" | sed 's/squaremap-\*/bluemap-*/' ;;
    *) echo "unknown modset $1" >&2; return 1 ;;
  esac
}
