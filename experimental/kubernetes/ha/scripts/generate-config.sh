python spinctl/spinctl.py "$@"

if [ $? -ne 0 ]; then
    exit $?
fi
