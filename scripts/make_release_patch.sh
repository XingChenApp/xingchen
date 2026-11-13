#!/usr/bin/env bash
# make_release_patch.sh — XingChen delta-update patch generator (generation side).
#
# Usage:
#   make_release_patch.sh <old-raw.apk> <new-raw.apk> <output-dir>
#
# Both inputs are CI-signed (not normalized) APKs. For each input the script
# runs the release pipeline mandated by ApkDiffPatch for v2-signed APKs:
#   production-key sign -> ApkNormalized -> apksigner(33.0.2) re-sign (v2 only)
# producing the "release form" APK. It then runs ZipDiff(release-old,
# release-new), packs the raw patch into the .xcpatch container, writes a
# manifest JSON next to it, and self-checks: ZipPatch rebuild must be
# byte-identical (SHA-256) to the release-form new APK and pass
# `apksigner verify`. Any failed step exits non-zero and names the step.
#
# .xcpatch container format (frozen; the client parses exactly this):
#   offset 0: 8 bytes ASCII magic "XCPATCH1"
#   next:     4 bytes big-endian unsigned int = header length N
#   next:     N bytes UTF-8 JSON header:
#             format, fromVersionCode, fromVersionName, toVersionCode,
#             toVersionName, baseApkSha256, targetApkSha256,
#             patchDataSha256, patchDataSize, targetSize
#   rest:     raw ZipDiff patch bytes
#
# Production key handling: the .key file is a custom length-prefixed
# container (magic acd223ab; [4:8] BE length + PEM cert segment; then BE
# length + unencrypted PKCS#8 DER private key). It is split with openssl /
# python3 into a mktemp directory only, the certificate fingerprint is
# verified against the 1.4.28 release signer before any signing, and all
# key material is shredded on exit. Key bytes are never echoed and never
# written anywhere else.
#
# Toolchain pins (see worknotes/xc-delta-update-design-20261114.md Phase 0):
#   - apksigner MUST be build-tools 33.0.2: newer apksigner (36/37) writes a
#     0xd935 alignment extra field per STORED entry that ZipPatch cannot
#     reproduce, breaking byte equality.
#   - ZipDiff/ZipPatch/ApkNormalized from sisong/ApkDiffPatch.
# Overrides (env): XC_DELTA_TOOLS, XC_PROD_KEY, XC_AAPT2, XC_JAVA_HOME.

set -euo pipefail

fail() { echo "FAIL[$1]: $2" >&2; exit 1; }

[ $# -eq 3 ] || fail "args" "usage: $0 <old-raw.apk> <new-raw.apk> <output-dir>"
OLD_RAW=$1; NEW_RAW=$2; OUTDIR=$3
[ -f "$OLD_RAW" ] || fail "args" "old apk not found: $OLD_RAW"
[ -f "$NEW_RAW" ] || fail "args" "new apk not found: $NEW_RAW"
mkdir -p "$OUTDIR" || fail "args" "cannot create output dir: $OUTDIR"
OUTDIR=$(cd "$OUTDIR" && pwd)
OLD_RAW=$(readlink -f "$OLD_RAW"); NEW_RAW=$(readlink -f "$NEW_RAW")

TOOLS_DIR=${XC_DELTA_TOOLS:-$HOME/workspace/tools}
ZIPDIFF=$TOOLS_DIR/apkdiffpatch/bin/ZipDiff
ZIPPATCH=$TOOLS_DIR/apkdiffpatch/bin/ZipPatch
APKNORM=$TOOLS_DIR/apkdiffpatch/bin/ApkNormalized
APKSIGNER=$TOOLS_DIR/build-tools-33.0.2/apksigner
KEY_FILE=${XC_PROD_KEY:-$HOME/workspace/user/files/xiaosa_2_7ubb.key}
# Release signer of 1.4.28 (apksigner verify --print-certs, SHA-256 digest).
EXPECTED_SIGNER_SHA256=2e1d87a9548a0e1a3c7d69f6a38bcc49510dde797237a6df0321e018fe269b70

for t in "$ZIPDIFF" "$ZIPPATCH" "$APKNORM" "$APKSIGNER"; do
  [ -x "$t" ] || fail "tools" "missing tool: $t"
done
[ -f "$KEY_FILE" ] || fail "tools" "production key file not found"

# aapt2 only reads metadata (any recent version is fine; the 33.0.2 ban
# applies to signing only).
AAPT2=${XC_AAPT2:-}
if [ -z "$AAPT2" ]; then
  for c in "$HOME/workspace/android-sdk/build-tools/android-37.0/aapt2" \
           "$TOOLS_DIR/build-tools-33.0.2/aapt2"; do
    [ -x "$c" ] && AAPT2=$c && break
  done
fi
[ -n "$AAPT2" ] && [ -x "$AAPT2" ] || fail "tools" "aapt2 not found"

# apksigner is a shell wrapper around a jar; it needs a JRE.
if ! command -v java >/dev/null 2>&1; then
  JH=${XC_JAVA_HOME:-$HOME/workspace/tools/jre17}
  [ -x "$JH/bin/java" ] || fail "tools" "java not found (set XC_JAVA_HOME)"
  export JAVA_HOME=$JH
  export PATH="$JH/bin:$PATH"
fi

WORK=$(mktemp -d)
chmod 700 "$WORK"
cleanup() {
  # Shred key material first, then drop the whole work dir.
  for f in "$WORK"/*.pem "$WORK"/*.pk8 "$WORK"/*.der "$WORK"/*.p12 "$WORK"/pass*.txt; do
    [ -f "$f" ] && shred -u "$f" 2>/dev/null || true
  done
  rm -rf "$WORK"
}
trap cleanup EXIT

sha256_of() { sha256sum "$1" | awk '{print $1}'; }
size_of() { stat -c '%s' "$1"; }

# ---- Step 1: split production key (temp dir only) -------------------------
python3 - "$KEY_FILE" "$WORK/cert.pem" "$WORK/key.der" <<'PYEOF'
import struct, sys
src, cert_out, key_out = sys.argv[1], sys.argv[2], sys.argv[3]
data = open(src, 'rb').read()
if len(data) < 12 or data[0:4].hex() != 'acd223ab':
    sys.exit('key container: bad magic')
len1 = struct.unpack('>I', data[4:8])[0]
cert = data[8:8 + len1]
if not cert.startswith(b'-----BEGIN CERTIFICATE-----'):
    sys.exit('key container: cert segment malformed')
off = 8 + len1
len2 = struct.unpack('>I', data[off:off + 4])[0]
key = data[off + 4:off + 4 + len2]
if len(key) != len2 or key[0] != 0x30:
    sys.exit('key container: private key segment malformed')
open(cert_out, 'wb').write(cert + b'\n')
open(key_out, 'wb').write(key)
PYEOF
[ $? -eq 0 ] || fail "key-split" "cannot split production key container"
chmod 600 "$WORK/cert.pem" "$WORK/key.der"
# PEM form of the key (validates loadability via pkey). apksigner 33.0.2
# refuses this key via --key/--cert PKCS#8 files ("Not an RSA, EC, or DSA
# private key"); the Phase-0-proven route is a PKCS#12 keystore, built
# below after the fingerprint check.
openssl pkey -inform DER -in "$WORK/key.der" -out "$WORK/key.pem" 2>/dev/null \
  || fail "key-split" "private key does not load via openssl pkey"
chmod 600 "$WORK/key.pem"

# ---- Step 2: verify signer fingerprint before signing ----------------------
CERT_FP=$(openssl x509 -in "$WORK/cert.pem" -noout -fingerprint -sha256 \
  | sed 's/.*=//' | tr -d ':' | tr 'A-F' 'a-f') \
  || fail "key-verify" "cannot read certificate fingerprint"
[ "$CERT_FP" = "$EXPECTED_SIGNER_SHA256" ] \
  || fail "key-verify" "certificate fingerprint does not match 1.4.28 release signer; refusing to sign"
# Key/cert pair must belong together: public keys must be identical.
PUB_FROM_KEY=$(openssl pkey -inform DER -in "$WORK/key.der" -pubout -outform DER 2>/dev/null | sha256sum | awk '{print $1}')
PUB_FROM_CERT=$(openssl x509 -in "$WORK/cert.pem" -pubkey -noout 2>/dev/null | openssl pkey -pubin -outform DER 2>/dev/null | sha256sum | awk '{print $1}')
[ "$PUB_FROM_KEY" = "$PUB_FROM_CERT" ] \
  || fail "key-verify" "private key does not match certificate public key"
echo "key: signer fingerprint verified"
# Repack cert+key as PKCS#12 with a throwaway random password (temp dir
# only, shredded on exit). apksigner consumes --ks-pass/--key-pass files
# line by line, so the same password goes into two separate files.
openssl rand -hex 20 > "$WORK/pass1.txt" || fail "key-pack" "cannot generate keystore password"
cp "$WORK/pass1.txt" "$WORK/pass2.txt"
chmod 600 "$WORK/pass1.txt" "$WORK/pass2.txt" "$WORK/ks.p12" 2>/dev/null || true
openssl pkcs12 -export -inkey "$WORK/key.pem" -in "$WORK/cert.pem" \
  -name xingchen -out "$WORK/ks.p12" -passout file:"$WORK/pass1.txt" 2>/dev/null \
  || fail "key-pack" "openssl pkcs12 -export failed"
chmod 600 "$WORK/ks.p12"

# ---- Step 3: release-form pipeline for one apk -----------------------------
# sign1 -> ApkNormalized -> sign2 (v2 only, apksigner 33.0.2)
sign_with_prod_key() { # $1=out $2=in
  "$APKSIGNER" sign --ks "$WORK/ks.p12" --ks-type PKCS12 \
    --ks-pass file:"$WORK/pass1.txt" --key-pass file:"$WORK/pass2.txt" \
    --ks-key-alias xingchen \
    --v1-signing-enabled false --v2-signing-enabled true \
    --v3-signing-enabled false --v4-signing-enabled false \
    --out "$1" "$2"
}
make_release_form() {
  local_in=$1; local_out=$2; local_tag=$3
  sign_with_prod_key "$WORK/$local_tag.signed1.apk" "$local_in" \
    || fail "sign1-$local_tag" "apksigner (33.0.2) first sign failed"
  "$APKNORM" "$WORK/$local_tag.signed1.apk" "$WORK/$local_tag.normalized.apk" \
    || fail "normalize-$local_tag" "ApkNormalized failed"
  sign_with_prod_key "$local_out" "$WORK/$local_tag.normalized.apk" \
    || fail "sign2-$local_tag" "apksigner (33.0.2) second sign failed"
  "$APKSIGNER" verify "$local_out" >/dev/null 2>&1 \
    || fail "verify-$local_tag" "apksigner verify failed on release-form apk"
}

OLD_REL=$OUTDIR/release-old.apk
NEW_REL=$OUTDIR/release-new.apk
make_release_form "$OLD_RAW" "$OLD_REL" old
echo "release-old: $(size_of "$OLD_REL") bytes sha256=$(sha256_of "$OLD_REL")"
make_release_form "$NEW_RAW" "$NEW_REL" new
echo "release-new: $(size_of "$NEW_REL") bytes sha256=$(sha256_of "$NEW_REL")"

# ---- Step 4: version metadata from badging ---------------------------------
badging_field() { # $1=apk $2=field-name (no head pipe: SIGPIPE under pipefail)
  local badge firstline
  badge=$("$AAPT2" dump badging "$1" 2>/dev/null) || return 1
  firstline=${badge%%$'\n'*}
  sed -n "s/.* $2='\([^']*\)'.*/\1/p" <<<"$firstline"
}
FROM_VC=$(badging_field "$OLD_RAW" versionCode)
FROM_VN=$(badging_field "$OLD_RAW" versionName)
TO_VC=$(badging_field "$NEW_RAW" versionCode)
TO_VN=$(badging_field "$NEW_RAW" versionName)
[ -n "$FROM_VC" ] && [ -n "$TO_VC" ] && [ -n "$FROM_VN" ] && [ -n "$TO_VN" ] \
  || fail "badging" "cannot read versionCode/versionName via aapt2 dump badging"
echo "versions: $FROM_VN($FROM_VC) -> $TO_VN($TO_VC)"

# ---- Step 5: ZipDiff --------------------------------------------------------
RAW_PATCH=$OUTDIR/xingchen-$FROM_VC-to-$TO_VC.raw.patch
"$ZIPDIFF" "$OLD_REL" "$NEW_REL" "$RAW_PATCH" \
  || fail "zipdiff" "ZipDiff failed"
echo "raw-patch: $(size_of "$RAW_PATCH") bytes sha256=$(sha256_of "$RAW_PATCH")"

# ---- Step 6: pack .xcpatch container + manifest ----------------------------
XCPATCH=$OUTDIR/xingchen-$FROM_VC-to-$TO_VC.xcpatch
MANIFEST=$OUTDIR/xingchen-$FROM_VC-to-$TO_VC.manifest.json
BASE_SHA=$(sha256_of "$OLD_REL"); TARGET_SHA=$(sha256_of "$NEW_REL")
PATCH_SHA=$(sha256_of "$RAW_PATCH"); PATCH_SIZE=$(size_of "$RAW_PATCH")
TARGET_SIZE=$(size_of "$NEW_REL")
python3 - "$RAW_PATCH" "$XCPATCH" "$MANIFEST" \
  "$FROM_VC" "$FROM_VN" "$TO_VC" "$TO_VN" \
  "$BASE_SHA" "$TARGET_SHA" "$PATCH_SHA" "$PATCH_SIZE" "$TARGET_SIZE" <<'PYEOF'
import json, struct, sys
(raw_patch, xcpatch_out, manifest_out, from_vc, from_vn, to_vc, to_vn,
 base_sha, target_sha, patch_sha, patch_size, target_size) = sys.argv[1:13]
header = {
    "format": 1,
    "fromVersionCode": int(from_vc),
    "fromVersionName": from_vn,
    "toVersionCode": int(to_vc),
    "toVersionName": to_vn,
    "baseApkSha256": base_sha,
    "targetApkSha256": target_sha,
    "patchDataSha256": patch_sha,
    "patchDataSize": int(patch_size),
    "targetSize": int(target_size),
}
header_bytes = json.dumps(header, ensure_ascii=False,
                          separators=(',', ':')).encode('utf-8')
patch_bytes = open(raw_patch, 'rb').read()
if len(patch_bytes) != int(patch_size):
    sys.exit('patch size changed while packing')
with open(xcpatch_out, 'wb') as f:
    f.write(b'XCPATCH1')
    f.write(struct.pack('>I', len(header_bytes)))
    f.write(header_bytes)
    f.write(patch_bytes)
import hashlib
manifest = {
    "fromVersionCode": int(from_vc),
    "fromVersionName": from_vn,
    "toVersion": to_vn,
    "toVersionCode": int(to_vc),
    "toVersionName": to_vn,
    "baseApkSha256": base_sha,
    "targetApkSha256": target_sha,
    "patchSha256": hashlib.sha256(open(xcpatch_out, 'rb').read()).hexdigest(),
    "patchSize": 8 + 4 + len(header_bytes) + len(patch_bytes),
    "patchDataSha256": patch_sha,
    "patchDataSize": int(patch_size),
    "fullSize": int(target_size),
}
with open(manifest_out, 'w', encoding='utf-8') as f:
    json.dump(manifest, f, ensure_ascii=False, indent=2)
    f.write('\n')
PYEOF
[ $? -eq 0 ] || fail "pack" "failed to pack .xcpatch container / manifest"
echo "xcpatch: $(size_of "$XCPATCH") bytes"

# ---- Step 7: self-check ------------------------------------------------------
# 7a: container round-trip — header parses, embedded patch hash matches.
python3 - "$XCPATCH" "$PATCH_SHA" "$PATCH_SIZE" <<'PYEOF' \
  || fail "selfcheck-container" ".xcpatch header/patch hash mismatch"
import hashlib, json, struct, sys
data = open(sys.argv[1], 'rb').read()
assert data[0:8] == b'XCPATCH1', 'bad magic'
n = struct.unpack('>I', data[8:12])[0]
header = json.loads(data[12:12 + n].decode('utf-8'))
patch = data[12 + n:]
assert hashlib.sha256(patch).hexdigest() == sys.argv[2], 'patch hash mismatch'
assert len(patch) == int(sys.argv[3]) == header['patchDataSize'], 'patch size mismatch'
assert header['patchDataSha256'] == sys.argv[2], 'header patch hash mismatch'
PYEOF
# 7b: ZipPatch rebuild must equal release-form new apk byte for byte.
REBUILT=$WORK/rebuilt.apk
"$ZIPPATCH" "$OLD_REL" "$RAW_PATCH" "$REBUILT" \
  || fail "selfcheck-zippatch" "ZipPatch failed during self-check"
REBUILT_SHA=$(sha256_of "$REBUILT")
[ "$REBUILT_SHA" = "$TARGET_SHA" ] \
  || fail "selfcheck-sha256" "rebuilt apk sha256 $REBUILT_SHA != target $TARGET_SHA"
"$APKSIGNER" verify "$REBUILT" >/dev/null 2>&1 \
  || fail "selfcheck-verify" "apksigner verify failed on rebuilt apk"
echo "selfcheck: ZipPatch rebuild sha256 == target, apksigner verify OK"
echo "OK: $XCPATCH"
