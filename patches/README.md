# Fixes carried into the image by `Dockerfile.s3s`

`Dockerfile.s3s` builds the archived mint suite with the SDK checkouts pinned in [`../.sdk-refs`](../.sdk-refs), and applies the patches in this directory to them. Upstream's [`Dockerfile`](../Dockerfile) is left untouched, so it still follows the newest release of every SDK.

The same file pins the two versions the build used to resolve from `releases/latest`: the mc client binary that `build/mc/install.sh` installs and the minio-go test source that `build/minio-go/install.sh` compiles. Those are pins, not patches, so they are recorded in [`../.sdk-refs`](../.sdk-refs) with the command that produced them; neither script falls back to the newest release when the pin is missing.

A fix may live here only when the failure it removes is caused by mint's own test code, or by a client library disagreeing with AWS, and never when the failure is caused by the S3 implementation under test. Each fix below answers that question. The answer is checked, not asserted: the case is run against a real MinIO server, which is the reference implementation these suites are written for, and against the s3s proxy. A case that fails on both is a client defect and may be fixed here; a case that passes on the reference server and fails only on the implementation under test is that implementation's problem and stays in its expected-failure list.

Fixes to the build itself, such as a download source that stopped existing, are recorded here as well; they change no test and retire no expected-failure entry, and the section says so.

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

**Cause**: `functional/FunctionalTest.java`, `writeObject()` (line 360 at the pinned revision), adds `x-amz-acl: bucket-owner-full-control` to a request whose URL was presigned without that header. At this revision the helpers live in `FunctionalTest.java`; `minio/minio-java` `master` moved them to `functional/TestMinioClient.java`, so searching today's upstream tree for the header finds nothing. SigV4 covers the `x-amz-*` headers, so a presigned request that carries an unsigned one is refused; AWS documents the same rule and rejects such requests. The pinned SDK gives the test no way out: `GetPresignedObjectUrlArgs` exposes extra query parameters, not extra headers, so the header cannot be signed into the URL from the caller side.

**Why this is not a weakened test**: `writeObject()` never asserted on the ACL. It wrote the bytes, and the case then reads the object back and compares checksums. Removing the header leaves exactly that property under test, which the comment in the patched source states at the call site.

**Expected-failure entry**: `getPresignedObjectUrl()`. The runner reaches this name twice (`[PUT]` and `[PUT, expiry]`) and the entry caps a single failure, because the suite aborts on the first one; the second case only starts being exercised once this is fixed.

**Patch**: `0002-minio-java-unsigned-x-amz-acl-on-presigned-put.patch`.

## 0003 - aws-sdk-ruby: the same unsigned `x-amz-acl` on a presigned PUT

This test belongs to mint itself, so it is edited in place rather than patched: `run/core/aws-sdk-ruby/aws-stub-tests.rb`, in `presignedPutObjectTest`.

**Symptom**: `presignedPut(bucket_name,file_name)` fails with "Expected to be created object does NOT exist": the presigned PUT was refused, so the object the case then looks for was never created.

**Cause**: the request adds `'x-amz-acl' => 'public-read'` to a presigned PUT URL that does not sign it, the same construction as 0002.

**Why this is not a weakened test**: the case still uploads through the presigned URL and then checks that the object exists and that its content matches the source file. The header was never asserted on, and the response of the PUT was not inspected either.

**Expected-failure entry**: `presignedPut(bucket_name,file_name)`. Fixing it also lets `presignedPost(...)` run again, a case the suite had stopped reaching.

## 0004 - mc: the host that served the client binary answers 410

**Symptom**: the image build stops in `build/mc/install.sh` with wget exit 8, "the server issued an error response", which fails `release.sh` and with it the build.

**Cause**: the script fetched the binary from `https://dl.minio.io/client/mc/release/linux-amd64/mc.${MC_VERSION}`, and that download host is retired: both `dl.min.io` and `dl.minio.io` answer `410 Gone` for the path. The version the script asks for already comes from the GitHub release tag, and that same release publishes the binary as an asset.

**Why this is not a weakened test**: it changes no test. The binary is the same release build, taken from the release that names it, and it is now verified against the `sha256sum` asset published next to it before being installed; the previous download had no integrity check at all.

**Expected-failure entries**: none, this is a build fix.

**Architecture**: the asset name, like the Go toolchain tarball in `preinstall.sh`, names `amd64`, so the image builds for amd64 only and the publish workflow carries a single platform for that reason.
## 0005 - aws-sdk-go-v2: a conditional delete expectation that contradicts Amazon S3

This test belongs to mint itself, so it is edited in place rather than patched: `run/core/aws-sdk-go-v2/main.go`, in `testConditionalDeleteWithWildcardMissing`.

**Symptom**: on a server that evaluates `If-Match` on `DeleteObject`, the case fails with `AWS SDK Go V2 expected PreconditionFailed error but got: operation error S3: DeleteObject ... api error NoSuchKey`. On the MinIO build the s3s E2E suite pins today, the case never runs at all: `testConditionalDeleteWithIncorrectETag` calls `failureLog(...).Fatal()`, which exits the test binary, so the two wildcard cases that follow it are never reached (that run logs six entries, not eight).

**Cause**: the case deletes a key that does not exist with `If-Match: *` and expects 412 `PreconditionFailed`. Amazon S3 answers **404 `NoSuchKey`** for that request — a wildcard precondition on a key that has no object cannot be satisfied by any version, so it is reported as a missing key rather than as a failed match. The same request measured against Amazon S3, against the pinned MinIO build and against Silo gives 404 on the first and third, and 204 (no precondition evaluation at all) on the second; the probe and its raw output are recorded in the s3s repository, in the topic that evaluated switching the E2E backend.

**Why this is not a weakened test**: the case still requires the delete to fail, and it still asserts on the error code; only the code it demands changes, from one Amazon S3 never returns to the one it documents. No assertion is dropped and no request is skipped. The other three cases in the family were re-measured against Amazon S3 and already match, so they are left alone: `ConditionalDeleteWithCorrectETag` (204, object gone), `ConditionalDeleteWithIncorrectETag` (412, object kept), `ConditionalDeleteWithWildcardExists` (204, object gone).

**Expected-failure entry**: none on the backend the s3s E2E job uses today. The case that fails there is `ConditionalDeleteWithIncorrectETag` — a backend that ignores the precondition — and this change does not touch it; because the binary exits at that case, the fixed case is not even reached. The `ConditionalDeleteWithIncorrectETag` entry can only be retired together with a backend that evaluates the precondition (the s3s evaluation of the Silo backend covers that switch), at which point no `aws-sdk-go-v2` case fails and the entry has to go, because a stale entry fails the gate.
## 0006 - aws-sdk-java-v2: the suite skipped itself in plaintext

This test belongs to mint itself, so it is edited in place rather than patched: `build/aws-sdk-java-v2/app/src/main/java/io/minio/awssdk/v2/tests/FunctionalTests.java`.

**Symptom**: over `http://` the `aws-sdk-java-v2` suite reports no result line at all. Seven of its cases return before doing anything, and what is left (`initTests`) only creates a bucket, so a full run counts the group as executed while it produces zero PASS or FAIL rows.

**Cause**: the cases were written for the HTTPS half of the suite and each one starts with `if (!enableHTTPS) { return; }` (`createBucket_test`, `createBucketWithVersion_test`, `uploadObject_test`, `uploadMultiPart_test`, `uploadMultiPartAsync_test`, `uploadObjectVersions_test`, `crtClientDownload_test`). Nothing else blocks them: `main` builds all three clients for both schemes and hands the plaintext branch an `http://` endpoint, the trust-all TLS context exists only in the HTTPS branch, and the checksum trailers the SDK adds by default are a server-facing question, not a client-side blocker.

**What the change does**: a case now returns only when neither `ENABLE_HTTPS=1` nor `ENABLE_HTTP_TESTS=1` is set, so `ENABLE_HTTP_TESTS=1` runs the seven cases over `http://` and leaving it unset reproduces the previous behaviour exactly (zero rows). The runner also executes every case inside its own `try`/`catch` and fails the process at the end when any case failed, instead of exiting at the first exception, so one failure no longer hides the cases after it; each case still logs its own PASS/FAIL line through `MintLogger` before it throws.

**Why this is not a weakened test**: nothing is asserted away. No expected value, request or check is removed or relaxed - the cases stop skipping themselves, and a failing case still fails the run. The switch is deliberate: with `ENABLE_HTTP_TESTS` unset the image behaves exactly like the previous one, so the same image can be compared against itself, and the two changes are separable.

**Expected-failure entries**: none yet. The first plaintext run decides which cases fail, and each entry is added after that run names the layer that owns it (server, proxy or the case itself).

