#!/usr/bin/env bash
# seed-options.sh GAMEDIR — skip first-run screens so --quickPlayMultiplayer goes straight to the server.
o=$1/options.txt; mkdir -p $1; touch $o
set_opt() { grep -q "^$1:" $o && sed -i "s/^$1:.*/$1:$2/" $o || echo "$1:$2" >> $o; }
set_opt onboardAccessibility false; set_opt skipMultiplayerWarning true; set_opt joinedFirstServer true
set_opt tutorialStep none; set_opt pauseOnLostFocus false; set_opt renderDistance 6; set_opt simulationDistance 5
set_opt narrator 0; set_opt soundCategory_master 0; set_opt maxFps 30
