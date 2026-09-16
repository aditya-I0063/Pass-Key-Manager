#!/bin/sh
# Fails if ModalBottomSheet is called outside SecureModalBottomSheet.
#
# Every sheet shows vault contents and every sheet is its own window, so each one has to carry
# FLAG_SECURE. Relying on review to remember that is how a screenshot-blocking hole gets added
# by someone doing something else.
set -e
WRAPPER='app/src/main/java/com/bhardwaj/passkey/presentation/screens/common/SecureModalBottomSheet.kt'
# The leading boundary is what stops this matching the wrapper's own name.
offenders=$(grep -rlnE '(^|[^A-Za-z])ModalBottomSheet\(' app/src/main --include='*.kt' \
    | grep -v "^$WRAPPER$" || true)
if [ -n "$offenders" ]; then
    echo "ModalBottomSheet called outside SecureModalBottomSheet:"
    echo "$offenders" | sed 's/^/  /'
    echo
    echo "Use SecureModalBottomSheet instead - it pins SecureFlagPolicy.SecureOn."
    exit 1
fi
echo "OK: every bottom sheet goes through SecureModalBottomSheet."
