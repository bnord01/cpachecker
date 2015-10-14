#!/bin/bash

# the location of the java command
[ -z "$JAVA" ] && JAVA=java

# the default heap size of the Java VM
DEFAULT_HEAP_SIZE="1200M"

platform="`uname -s`"

# where the project directory is, relative to the location of this script
case "$platform" in
  Linux|CYGWIN*)
    SCRIPT="$(readlink -f "$0")"
    [ -n "$PATH_TO_CPACHECKER" ] || PATH_TO_CPACHECKER="$(readlink -f "$(dirname "$SCRIPT")/..")"
    ;;
  # other platforms like Mac don't support readlink -f
  *)
    [ -n "$PATH_TO_CPACHECKER" ] || PATH_TO_CPACHECKER="$(dirname "$0")/.."
    ;;
esac

if [ ! -e "$PATH_TO_CPACHECKER/bin/org/sosy_lab/cpachecker/cmdline/CPAMain.class" ] ; then
  if [ ! -e "$PATH_TO_CPACHECKER/cpachecker.jar" ] ; then
    echo "Could not find CPAchecker binary, please check path to project directory" 1>&2
    exit 1
  fi
fi

RESULTFILE="`pwd`/results.txt"
CPA="$PATH_TO_CPACHECKER/scripts/cpa.sh"
rm -f "$RESULTFILE"
touch "$RESULTFILE"

COUNT=0

while [ $# -gt 0 ]; do
    find $1 -type f -iname "*.c" -print0 | while IFS= read -r -d $'\0' file; do
        ((COUNT++))
        FILENAME="`basename $file`"    
        SCRIPTFILE="$PATH_TO_CPACHECKER/output/${FILENAME}.ifc.sh"
        QGOUTFILE="$PATH_TO_CPACHECKER/output/ifcQueryGen_${FILENAME}.out"

        
        echo "Running querygen on $file"    
        "$CPA" -ifcQueryGen "$file" > "$QGOUTFILE"
        
        if grep --quiet TRUE "$QGOUTFILE"
        then
            echo "Running queries for $file"
            pushd "$PATH_TO_CPACHECKER" > /dev/null 
            bash "$SCRIPTFILE" "$RESULTFILE"
            popd > /dev/null
        else
            echo "Error running query gen on $file"
            cat "$GQOUTFILE"
        fi
    done   
    
    shift
done


TOTAL=0
SECUREPA=0
INSECUREPA=0
ERRORPA=0
SECURELA=0
INSECURELA=0
ERRORLA=0

while read line
do           
      res=($line)
      ((TOTAL+=${res[0]}))
      ((SECUREPA+=${res[1]}))
      ((INSECUREPA+=${res[2]}))
      ((ERRORPA+=${res[3]}))
      ((SECURELA+=${res[4]}))
      ((INSECURELA+=${res[5]}))
      ((ERRORLA+=${res[6]}))    
done <"$RESULTFILE"

echo "Processd $COUNT programs with a total of $TOTAL queries, results (secure/insecure/error):"
echo "Predicate analysis: $SECUREPA / $INSECUREPA / $ERRORPA"
echo "Location analysis:  $SECURELA / $INSECURELA / $ERRORLA"
