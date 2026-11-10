run(){ n=$1; shift; timeout 2400 bash /tmp/run.sh "$@" ; }
bash /tmp/run.sh dev_ovvar /tmp/dev :mods:ovvar:runClientGameTest &
P=$!; ( sleep 2400; pkill -f runClientGameTest; pkill -f Xvfb ) & W=$!; wait $P; kill $W 2>/dev/null
for spec in "branch_ovvar :mods:ovvar:runClientGameTest" "kultur :mods:metacraft-kultur:runClientGameTest" "kultur_plain :mods:metacraft-kultur:runClientGameTest -PkulturNoBrewery -PkulturNoMoredyes"; do
 set -- $spec; n=$1; shift
 bash /tmp/run.sh $n /home/user/metamods "$@" &
 P=$!; ( sleep 2400; pkill -f runClientGameTest; pkill -f Xvfb ) & W=$!; wait $P; kill $W 2>/dev/null
done
