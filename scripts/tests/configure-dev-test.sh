#!/usr/bin/env bash
set -euo pipefail
umask 077
source_script=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)/configure-dev.sh
sandbox=$(mktemp -d)
trap 'rm -rf -- "$sandbox"' EXIT
mkdir -p "$sandbox/project/scripts" "$sandbox/bin" "$sandbox/elsewhere" "$sandbox/sdk space:=#!\\path"
cp -- "$source_script" "$sandbox/project/scripts/configure-dev.sh"
export REAL_MV
REAL_MV=$(command -v mv)
export PATH="$sandbox/bin:$PATH"
export ANDROID_HOME="$sandbox/sdk space:=#!\\path"
unset ANDROID_SDK_ROOT
export PASS_CALLS="$sandbox/calls" PASS_ID=123456789 PASS_HASH=0123456789abcdefABCDEF0123456789 PASS_FAIL=''
cat > "$sandbox/bin/pass" <<'PASS'
#!/usr/bin/env bash
set -eu
printf '%s\n' "$*" >> "$PASS_CALLS"
[[ $# == 2 && $1 == show ]] || exit 99
if [[ $2 == "$PASS_FAIL" ]]; then
    printf '%s\n' "$PASS_ID" "$PASS_HASH" >&2
    printf '%s\n' "$PASS_HASH"
    exit 1
fi
case $2 in
    tinygram/api_id) printf '%s\n' "$PASS_ID" ;;
    tinygram/api_hash) printf '%s\n' "$PASS_HASH" ;;
    *) exit 99 ;;
esac
PASS
chmod +x "$sandbox/bin/pass"
cat > "$sandbox/bin/mv" <<'MOVE'
#!/usr/bin/env bash
if [[ ${FAIL_MOVE:-} == 1 ]]; then
    printf '%s\n' "$PASS_HASH" >&2
    exit 1
fi
exec "$REAL_MV" "$@"
MOVE
chmod +x "$sandbox/bin/mv"
file=$sandbox/project/local.properties
run() {
    (cd "$sandbox/elsewhere" && bash -x "$sandbox/project/scripts/configure-dev.sh") > "$sandbox/out" 2> "$sandbox/err"
}
private_output() {
    if grep -F -e "$PASS_HASH" -e "$PASS_ID" "$sandbox/out" "$sandbox/err" >/dev/null; then
        printf 'Unexpected credential output\n' >&2
        exit 1
    fi
    [[ ! -s $sandbox/out ]]
    [[ -z $(find "$sandbox/project" -name '.local.properties.*' -print) ]]
}
reject() {
    cp -- "$file" "$sandbox/before"
    if run; then printf 'Expected rejection\n' >&2; exit 1; fi
    cmp -- "$file" "$sandbox/before"
    private_output
}

run
private_output
[[ $(stat -c %a "$file") == 600 ]]
printf 'show tinygram/api_id\nshow tinygram/api_hash\n' > "$sandbox/expected-calls"
cmp "$PASS_CALLS" "$sandbox/expected-calls"
grep -Fx "API_ID=$PASS_ID" "$file" >/dev/null
grep -Fx "API_HASH=$PASS_HASH" "$file" >/dev/null
grep -F 'sdk.dir=' "$file" | grep -F 'sdk\ space\:\=\#\!\\path' >/dev/null
[[ ! -e $sandbox/elsewhere/local.properties ]]

printf '# comment\nRELEASE_STORE_FILE=some\\ path\ncustom=value\\:more\n API_ID : 0000000000000000001\nAPI_HASH %s\nsdk.dir=old\n' "$PASS_HASH" > "$file"
head -n 5 "$file" > "$sandbox/preserved"
: > "$PASS_CALLS"
chmod 644 "$file"
run
private_output
[[ ! -s $PASS_CALLS && $(stat -c %a "$file") == 600 ]]
head -n 5 "$file" > "$sandbox/after"
cmp "$sandbox/preserved" "$sandbox/after"
cp "$file" "$sandbox/valid"

for entry in 'API_ID=0' 'API_ID=-1' 'API_ID=2147483648' 'API_ID=your_id' 'API_HASH=placeholder' 'API_ID=1\nAPI_ID=2' 'sdk.dir=a\nsdk.dir=b' 'API\\_ID=1' "API_ID=1\\\\" 'custom=x\\\nAPI_ID=1'; do
    printf '%b\n' "$entry" > "$file"
    reject
    [[ ! -s $PASS_CALLS ]]
done
printf 'API_ID=1\0002\n' > "$file"
reject
[[ ! -s $PASS_CALLS ]]

for bad in 0 2147483648 999999999999999999999999999999999999 '123x' $'123\n456'; do
    printf 'unrelated=kept\n' > "$file"
    PASS_ID=$bad reject
done
for bad in short 0123456789abcdef0123456789abcdeg $'0123456789abcdef0123456789abcdef\nextra'; do
    printf 'unrelated=kept\n' > "$file"
    PASS_HASH=$bad reject
done
for entry in tinygram/api_id tinygram/api_hash; do
    printf 'unrelated=kept\n' > "$file"
    PASS_FAIL=$entry reject
done
rm "$file"
if PASS_FAIL=tinygram/api_hash run; then exit 1; fi
[[ ! -e $file ]]
private_output

# A failed final rename must leave both contents and permissions unchanged.
cp "$sandbox/valid" "$file"
chmod 644 "$file"
FAIL_MOVE=1 reject
[[ $(stat -c %a "$file") == 644 ]]

cp "$sandbox/valid" "$file"
: > "$PASS_CALLS"
ANDROID_HOME="$sandbox/missing" reject
[[ ! -s $PASS_CALLS ]]
(
    export ANDROID_SDK_ROOT=$ANDROID_HOME
    export ANDROID_HOME=''
    run
)
private_output
[[ ! -s $PASS_CALLS ]]

# Missing only one credential must fetch only that entry.
printf 'API_ID=2147483647\n' > "$file"
run
printf 'show tinygram/api_hash\n' > "$sandbox/expected-calls"
cmp "$PASS_CALLS" "$sandbox/expected-calls"
private_output

# Preserve an existing hash while fetching only the missing ID.
printf 'API_HASH=%s' "$PASS_HASH" > "$file"
: > "$PASS_CALLS"
run
printf 'show tinygram/api_id\n' > "$sandbox/expected-calls"
cmp "$PASS_CALLS" "$sandbox/expected-calls"
private_output

cp "$file" "$sandbox/target"
: > "$PASS_CALLS"
rm "$file"
ln -s "$sandbox/target" "$file"
cp "$sandbox/target" "$sandbox/before"
if run; then exit 1; fi
cmp "$sandbox/target" "$sandbox/before"
[[ ! -s $PASS_CALLS ]]
private_output
printf 'configure-dev tests passed\n'
