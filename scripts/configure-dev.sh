#!/usr/bin/env bash
set +x
set -euo pipefail
umask 077
export LC_ALL=C

fail() {
    printf '%s\n' "tinygram-configure: $1" >&2
    exit 1
}

root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
file=$root/local.properties
tmp=
trap '[[ -z $tmp ]] || rm -f -- "$tmp"' EXIT
trap 'exit 1' HUP INT TERM

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
[[ -n $sdk && $sdk == /* && -d $sdk && $sdk != *$'\n'* && $sdk != *$'\r'* ]] ||
    fail 'Set ANDROID_HOME or ANDROID_SDK_ROOT to an existing absolute SDK directory.'
[[ $sdk != *[![:print:][:blank:]]* ]] ||
    fail 'Use an ASCII SDK path without control characters.'
[[ ! -L $file && ( ! -e $file || -f $file ) ]] ||
    fail 'local.properties must be a regular file, not a symbolic link.'

valid_id() {
    local number=$1
    [[ $number =~ ^[0-9]+$ ]] || return 1
    while [[ $number == 0* && ${#number} -gt 1 ]]; do number=${number:1}; done
    [[ ${#number} -le 10 ]] && (( 10#$number > 0 && 10#$number <= 2147483647 ))
}
valid_hash() {
    [[ $1 =~ ^[[:xdigit:]]{32}$ ]]
}

# Reject escaped keys and continuations rather than partially interpreting Java properties.
configure() {
    local line trimmed key value id='' hash='' seen_id=0 seen_hash=0 seen_sdk=0
    tmp=$(mktemp "$root/.local.properties.XXXXXXXX") || return 1
    if [[ -e $file ]]; then
        awk 'index($0, sprintf("%c", 0)) { exit 1 }' "$file" || return 1
        while IFS= read -r line || [[ -n $line ]]; do
            trimmed=${line#"${line%%[!$' \t\f']*}"}
            trimmed=${trimmed%$'\r'}
            [[ $trimmed != *$'\r'* ]] || return 1
            if [[ -z $trimmed || $trimmed == \#* || $trimmed == \!* ]]; then
                printf '%s\n' "$line" >> "$tmp" || return 1
                continue
            fi
            [[ $trimmed != *\\ ]] || return 1
            key=${trimmed%%[=:$' \t\f']*}
            [[ $key != *\\* ]] || return 1
            case $key in
                API_ID|API_HASH|sdk.dir)
                    value=${trimmed#"$key"}
                    value=${value#"${value%%[!$' \t\f']*}"}
                    if [[ $value == [=:]* ]]; then
                        value=${value:1}
                        value=${value#"${value%%[!$' \t\f']*}"}
                    fi
                    case $key in
                        API_ID)
                            (( seen_id == 0 )) && valid_id "$value" || return 1
                            seen_id=1; id=$value ;;
                        API_HASH)
                            (( seen_hash == 0 )) && valid_hash "$value" || return 1
                            seen_hash=1; hash=$value ;;
                        sdk.dir)
                            (( seen_sdk == 0 )) || return 1
                            seen_sdk=1
                            continue ;;
                    esac ;;
            esac
            printf '%s\n' "$line" >> "$tmp" || return 1
        done < "$file" || return 1
    fi
    if (( seen_id == 0 )); then
        id=$(pass show tinygram/api_id 2>/dev/null) || return 1
        valid_id "$id" || return 1
        printf 'API_ID=%s\n' "$id" >> "$tmp" || return 1
    fi
    if (( seen_hash == 0 )); then
        hash=$(pass show tinygram/api_hash 2>/dev/null) || return 1
        valid_hash "$hash" || return 1
        printf 'API_HASH=%s\n' "$hash" >> "$tmp" || return 1
    fi
    # Escape Java property value metacharacters, including leading spaces.
    sdk=${sdk//\\/\\\\}
    sdk=${sdk// /\\ }
    sdk=${sdk//$'\t'/\\t}
    sdk=${sdk//$'\f'/\\f}
    sdk=${sdk//:/\\:}
    sdk=${sdk//=/\\=}
    sdk=${sdk//#/\\#}
    sdk=${sdk//!/\\!}
    printf 'sdk.dir=%s\n' "$sdk" >> "$tmp" || return 1
    chmod 600 "$tmp" || return 1
    mv -f -- "$tmp" "$file" || return 1
    tmp=
}

# Suppress diagnostics from tools too: pass and filesystem errors may contain secrets.
if ! configure 2>/dev/null; then
    fail 'Configuration unchanged. Check SDK access and pass entries tinygram/api_id and tinygram/api_hash. Fix invalid or duplicate credentials in local.properties manually, and simplify escaped keys or continued properties before retrying.'
fi
