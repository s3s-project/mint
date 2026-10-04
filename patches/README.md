# Fixes carried into the image by `Dockerfile.s3s`

`Dockerfile.s3s` builds the archived mint suite with the SDK checkouts pinned in [`../.sdk-refs`](../.sdk-refs), and applies the patches in this directory to them. Upstream's [`Dockerfile`](../Dockerfile) is left untouched, so it still follows the newest release of every SDK.

A fix may live here only when the failure it removes is caused by mint's own test code, or by a client library disagreeing with AWS, and never when the failure is caused by the S3 implementation under test. Each fix below answers that question. The answer is checked, not asserted: the case is run against a real MinIO server, which is the reference implementation these suites are written for, and against the s3s proxy. A case that fails on both is a client defect and may be fixed here; a case that passes on the reference server and fails only on the implementation under test is that implementation's problem and stays in its expected-failure list.

Where a fix retires an entry of the expected-failure list in the s3s repository (`xtask/src/report/mint.rs`, `EXPECTED_FAILURES`), the entry has to be removed in the same change, because the gate reports an entry whose test no longer fails at all as stale. Fixing a case that the runner aborted on can also make the cases after it run for the first time, so a change here is verified with a full sweep of both images, not with the patched case alone.

The patches are applied by `build/*/install.sh` right after the pinned checkout, and a patch that stops applying fails the build instead of producing an image with unpatched test sources. After bumping a revision in [`../.sdk-refs`](../.sdk-refs), re-apply the patch to the new checkout, adjust it if upstream changed the code, and repeat the full sweep.

## 0001 - minio-js: a copy condition built from a field the SDK does not return

**Symptom**: the `minio-js` suite fails at the `copyObject(...)` step that copies with an "Unmodified since" condition, and that failure takes `listObjects(bucketName, prefix, recursive)` and the suite's `"after all"` hook down with it.

**Cause**: `tests/functional/functional-tests.js` reads `stat.modifiedDate` from the response of `statObject` (line 732 at the pinned revision, line 759 on `minio/minio-js` `master`), but that response carries the field as `lastModified` (`src/internal/type.ts`, `StatObjectResponse`). The test stores `undefined`, turns it into `new Date(undefined)` and sends an unusable `x-amz-copy-source-if-unmodified-since` value, which no server can satisfy. This is a client-side defect regardless of the server: the value never leaves the test.

**Why this is not a weakened test**: the step exists to prove that a conditional copy succeeds when the source has not changed since a date read from `statObject`, and reading the member the SDK actually returns is what makes it test that. No assertion is dropped and no request is skipped.

**Expected-failure entries**: `copyObject(bucketName, objectName, srcObject, conditions, cb)`, `listObjects(bucketName, prefix, recursive)` and `"after all" hook in "functional tests"`.

**Patch**: `0001-minio-js-stat-object-last-modified.patch`.

## 0002 - minio-java: an unsigned `x-amz-acl` on a presigned PUT

**Symptom**: `getPresignedObjectUrl()` fails on its `[PUT]` case with a 403 from the server ("failed to create object"). The runner rethrows on failure, so the suite stops there and every case after it never runs.

**Cause**: `functional/FunctionalTest.java`, `writeObject()` (line 360 at the pinned revision), adds `x-amz-acl: bucket-owner-full-control` to a request whose URL was presigned without that header. SigV4 covers the `x-amz-*` headers, so a presigned request that carries an unsigned one is refused; AWS documents the same rule and rejects such requests. The pinned SDK gives the test no way out: `GetPresignedObjectUrlArgs` exposes extra query parameters, not extra headers, so the header cannot be signed into the URL from the caller side.

**Why this is not a weakened test**: `writeObject()` never asserted on the ACL. It wrote the bytes, and the case then reads the object back and compares checksums. Removing the header leaves exactly that property under test, which the comment in the patched source states at the call site.

**Expected-failure entry**: `getPresignedObjectUrl()`. The runner reaches this name twice (`[PUT]` and `[PUT, expiry]`) and the entry caps a single failure, because the suite aborts on the first one; the second case only starts being exercised once this is fixed.

**Patch**: `0002-minio-java-unsigned-x-amz-acl-on-presigned-put.patch`.

## 0003 - aws-sdk-ruby: the same unsigned `x-amz-acl` on a presigned PUT

This test belongs to mint itself, so it is edited in place rather than patched: `run/core/aws-sdk-ruby/aws-stub-tests.rb`, in `presignedPutObjectTest`.

**Symptom**: `presignedPut(bucket_name,file_name)` fails with "Expected to be created object does NOT exist": the presigned PUT was refused, so the object the case then looks for was never created.

**Cause**: the request adds `'x-amz-acl' => 'public-read'` to a presigned PUT URL that does not sign it, the same construction as 0002.

**Why this is not a weakened test**: the case still uploads through the presigned URL and then checks that the object exists and that its content matches the source file. The header was never asserted on, and the response of the PUT was not inspected either.

**Expected-failure entry**: `presignedPut(bucket_name,file_name)`. Fixing it also lets `presignedPost(...)` run again, a case the suite had stopped reaching.
