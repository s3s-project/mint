/*
 *  Mint, (C) 2018-2023 Minio, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.minio.awssdk.v2.tests;

import software.amazon.awssdk.auth.credentials.*;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.core.internal.http.loader.DefaultSdkHttpClientBuilder;
import software.amazon.awssdk.core.waiters.WaiterResponse;
import software.amazon.awssdk.http.crt.AwsCrtAsyncHttpClient;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.utils.AttributeMap;

import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class FunctionalTests {
    private static final String PASS = "PASS";
    private static final String FAILED = "FAIL";
    private static final String IGNORED = "NA";

    private static String accessKey;
    private static String secretKey;
    private static Region region;
    private static String endpoint;
    private static boolean enableHTTPS;
    // Opt-in switch: the seven cases below were written for the HTTPS suite and
    // returned early in plaintext. Setting ENABLE_HTTP_TESTS=1 lets them run
    // over http:// as well; leaving it unset keeps the previous behaviour.
    private static boolean enableHTTPTests;

    private static final List<String> bucketsList = new ArrayList<>();

    private static final Random random = new Random(new SecureRandom().nextLong());
    private static final String bucketName = getRandomName();
    private static boolean mintEnv = false;

    private static String file1Kb;
    private static String file1Mb;
    private static String file6Mb;

    private static S3Client s3Client;
    private static S3AsyncClient s3AsyncClient;
    private static S3AsyncClient s3CrtAsyncClient;
    private static S3TestUtils s3TestUtils;

    public static String getRandomName() {
        return "aws-sdk-java-v2-test-" + new BigInteger(32, random).toString(32);
    }

    /**
     * Prints a success log entry in JSON format.
     */
    public static void mintSuccessLog(String function, String args, long startTime) {
        if (mintEnv) {
            System.out.println(
                    new MintLogger(function, args, System.currentTimeMillis() - startTime, PASS, null, null, null));
        }
    }

    /**
     * Prints a failure log entry in JSON format.
     */
    public static void mintFailedLog(String function, String args, long startTime, String message, String error) {
        if (mintEnv) {
            System.out.println(new MintLogger(function, args, System.currentTimeMillis() - startTime, FAILED, null,
                    message, error));
        }
    }

    /**
     * Prints a ignore log entry in JSON format.
     */
    public static void mintIgnoredLog(String function, String args, long startTime) {
        if (mintEnv) {
            System.out.println(
                    new MintLogger(function, args, System.currentTimeMillis() - startTime, IGNORED, null, null, null));
        }
    }

    public static void initTests() throws IOException {
        // Create bucket
        s3Client.createBucket(CreateBucketRequest
                .builder()
                .bucket(bucketName)
                .build());
        s3Client.waiter().waitUntilBucketExists(HeadBucketRequest
                .builder()
                .bucket(bucketName)
                .build());
        bucketsList.add(bucketName);
    }

    // Run tests
    //
    // Every case runs in its own try/catch. The runner used to rethrow straight
    // out of runTests(), and main exits on the first exception, so one failing
    // case hid every case after it. A case that fails still fails the process at
    // the end (main keeps its non-zero exit), and each case still logs its own
    // PASS/FAIL line before it throws.
    public static void runTests() throws Exception {
        List<String> failed = new ArrayList<>();
        runCase("createBucket_test", failed, FunctionalTests::createBucket_test);
        runCase("createBucketWithVersion_test", failed, FunctionalTests::createBucketWithVersion_test);
        runCase("uploadObject_test", failed, FunctionalTests::uploadObject_test);
        runCase("uploadMultiPart_test", failed, FunctionalTests::uploadMultiPart_test);
        runCase("uploadMultiPartAsync_test", failed, FunctionalTests::uploadMultiPartAsync_test);
        runCase("uploadObjectVersions_test", failed, FunctionalTests::uploadObjectVersions_test);
        runCase("crtClientDownload_test", failed, FunctionalTests::crtClientDownload_test);
//        runCase("uploadSnowballObjects_test", failed, FunctionalTests::uploadSnowballObjects_test);
        if (!failed.isEmpty()) {
            throw new Exception("failed test cases: " + String.join(", ", failed));
        }
    }

    private interface TestCase {
        void run() throws Exception;
    }

    private static void runCase(String name, List<String> failed, TestCase test) {
        try {
            test.run();
        } catch (Exception e) {
            failed.add(name);
            System.out.println("case failed: " + name + " : " + e);
        }
    }

    public static void createBucket_test() throws Exception {
        if (!mintEnv) {
            System.out.println("Test: S3Client.createBucket");
        }
        if (!enableHTTPS && !enableHTTPTests) {
            return;
        }

        String bucket = getRandomName();
        long startTime = System.currentTimeMillis();
        try {
            s3Client.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucket)
                    .build());
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest
                    .builder()
                    .bucket(bucket)
                    .build());
            bucketsList.add(bucket);
            mintSuccessLog("S3Client.createBucket", "bucket: " + bucket, startTime);
        } catch (Exception ex) {
            mintFailedLog(
                    "S3Client.createBucket",
                    "bucket: " + bucket,
                    startTime,
                    null,
                    ex.toString() + " >>> " + Arrays.toString(ex.getStackTrace()));
            throw ex;
        }
    }

    public static void createBucketWithVersion_test() throws Exception {
        if (!mintEnv) {
            System.out.println("Test: S3Client.createBucket");
        }
        if (!enableHTTPS && !enableHTTPTests) {
            return;
        }

        String bucket = getRandomName();
        long startTime = System.currentTimeMillis();
        try {
            s3Client.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucket)
                    .build());
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest
                    .builder()
                    .bucket(bucket)
                    .build());
            s3Client.putBucketVersioning(PutBucketVersioningRequest
                    .builder()
                    .bucket(bucket)
                    .versioningConfiguration(VersioningConfiguration
                            .builder()
                            .status(BucketVersioningStatus.ENABLED)
                            .build())
                    .build());
            bucketsList.add(bucket);
            mintSuccessLog("S3Client.putBucketVersioning", "bucket: " + bucket, startTime);
        } catch (Exception ex) {
            mintFailedLog(
                    "S3Client.putBucketVersioning",
                    "bucket: " + bucket,
                    startTime,
                    null,
                    ex.toString() + " >>> " + Arrays.toString(ex.getStackTrace()));
            throw ex;
        }
    }

    public static void uploadObject_test() throws Exception {
        if (!mintEnv) {
            System.out.println("Test: S3Client.putObject");
        }
        if (!enableHTTPS && !enableHTTPTests) {
            return;
        }

        long startTime = System.currentTimeMillis();
        String file1KbMD5 = Utils.getFileMD5(file1Kb);
        String objectName = "testobject";
        try {
            s3TestUtils.uploadObject(bucketName, objectName, file1Kb);
            s3TestUtils.downloadObject(bucketName, objectName, file1KbMD5);
            mintSuccessLog(
                    "S3Client.putObject",
                    "bucket: " + bucketName + ", object: " + objectName + ", String: " + file1Kb,
                    startTime);
        } catch (Exception ex) {
            mintFailedLog("S3Client.putObject",
                    "bucket: " + bucketName + ", object: " + objectName + ", String: " + file1Kb,
                    startTime,
                    null,
                    ex.toString() + " >>> " + Arrays.toString(ex.getStackTrace()));
            throw ex;
        }
    }

    public static void uploadMultiPart_test() throws Exception {
        if (!mintEnv) {
            System.out.println("Test: S3Client.uploadPart");
        }
        if (!enableHTTPS && !enableHTTPTests) {
            return;
        }

        long startTime = System.currentTimeMillis();
        String objectName = "testobject";
        try {
            s3TestUtils.uploadMultipartObject(bucketName, objectName);
            s3TestUtils.downloadObject(bucketName, objectName, "");
            mintSuccessLog(
                    "S3Client.uploadPart",
                    "bucket: " + bucketName + ", object: " + objectName,
                    startTime);
        } catch (Exception ex) {
            mintFailedLog(
                    "S3Client.uploadPart",
                    "bucket: " + bucketName + ", object: " + objectName,
                    startTime,
                    null,
                    ex.toString() + " >>> " + Arrays.toString(ex.getStackTrace()));
            throw ex;
        }
    }

    public static void uploadMultiPartAsync_test() throws Exception {
        if (!mintEnv) {
            System.out.println("Test: Async S3Client.uploadPart");
        }
        if (!enableHTTPS && !enableHTTPTests) {
            return;
        }

        long startTime = System.currentTimeMillis();
        String objectName = "testobject";
        try {
            s3TestUtils.uploadMultipartObjectAsync(bucketName, objectName);
            s3TestUtils.downloadObject(bucketName, objectName, "");
            mintSuccessLog(
                    "Async S3Client.uploadPart",
                    "bucket: " + bucketName + ", object: " + objectName,
                    startTime);
        } catch (Exception ex) {
            mintFailedLog(
                    "Async S3Client.uploadPart",
                    "bucket: " + bucketName + ", object: " + objectName,
                    startTime,
                    null,
                    ex.toString() + " >>> " + Arrays.toString(ex.getStackTrace()));
            throw ex;
        }
    }

    public static void uploadObjectVersions_test() throws Exception {
        if (!mintEnv) {
            System.out.println("Test: S3Client.putObject versions");
        }
        if (!enableHTTPS && !enableHTTPTests) {
            return;
        }

        String bucket = getRandomName();
        long startTime = System.currentTimeMillis();
        String objectName = "testobject";
        try {
            s3Client.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucket)
                    .build());
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest
                    .builder()
                    .bucket(bucket)
                    .build());
            s3Client.putBucketVersioning(PutBucketVersioningRequest
                    .builder()
                    .bucket(bucket)
                    .versioningConfiguration(VersioningConfiguration
                            .builder()
                            .status(BucketVersioningStatus.ENABLED)
                            .build())
                    .build());
            // load same object multiple times
            s3TestUtils.uploadObject(bucketName, objectName, file1Kb);
            s3TestUtils.uploadObject(bucketName, objectName, file1Kb);
            s3TestUtils.uploadObject(bucketName, objectName, file1Kb);
            s3TestUtils.downloadObject(bucketName, objectName, "");

            bucketsList.add(bucket);

            mintSuccessLog("S3Client.putObject versions",
                    "bucket: " + bucket + ", object: " + objectName,
                    startTime);
        } catch (Exception ex) {
            mintFailedLog("S3Client.putObject versions",
                    "bucket: " + bucket + ", object: " + objectName,
                    startTime,
                    null,
                    ex.toString() + " >>> " + Arrays.toString(ex.getStackTrace()));
            throw ex;
        }
    }

    public static void crtClientDownload_test() throws Exception {
        if (!mintEnv) {
            System.out.println("Test: Async S3CrtClient.getObject");
        }
        if (!enableHTTPS && !enableHTTPTests) {
            return;
        }

        String bucket = getRandomName();
        long startTime = System.currentTimeMillis();
        String objectName = "testobject";
        try {
            s3Client.createBucket(CreateBucketRequest
                    .builder()
                    .bucket(bucket)
                    .build());
            s3Client.waiter().waitUntilBucketExists(HeadBucketRequest
                    .builder()
                    .bucket(bucket)
                    .build());
	    Path downloadPath = Path.of("/tmp/test");
	    long expectedSize = Path.of(file1Kb).toFile().length();
	    s3CrtAsyncClient.putObject(
		    r -> r.bucket(bucket).key(objectName), AsyncRequestBody.fromFile(Path.of(file1Kb))
		    ).join();
	    s3CrtAsyncClient.getObject(
		    r -> r.bucket(bucket).key(objectName), downloadPath
		    ).join();
	    long downloadedSize = downloadPath.toFile().length();
	    if (downloadedSize != expectedSize) {
		throw new IOException("downloaded " + downloadedSize + " bytes, expected " + expectedSize);
	    }

            bucketsList.add(bucket);

	    mintSuccessLog("Async S3CrtClient.getObject versions",
                    "bucket: " + bucket + ", object: " + objectName,
                    startTime);
        } catch (Exception ex) {
            mintFailedLog("Async S3CrtClient.getObject",
                    "bucket: " + bucket + ", object: " + objectName,
                    startTime,
                    null,
                    ex.toString() + " >>> " + Arrays.toString(ex.getStackTrace()));
            throw ex;
        }
    }

    public static void teardown() throws IOException {
        for (String bkt : bucketsList) {
            // Remove all objects under the test bucket & the bucket itself
            ListObjectsV2Request request = ListObjectsV2Request
                    .builder()
                    .bucket(bkt)
                    .build();
            ListObjectsV2Response listObjectsResponse;
            do {
                listObjectsResponse = s3Client.listObjectsV2(request);
                for (S3Object obj : listObjectsResponse.contents()) {
                    s3Client.deleteObject(DeleteObjectRequest
                            .builder()
                            .bucket(bkt)
                            .key(obj.key())
                            .build());
                }
            } while (listObjectsResponse.isTruncated());
            // finally remove the bucket
            s3Client.deleteBucket(DeleteBucketRequest
                    .builder()
                    .bucket(bkt)
                    .build());
        }
    }

    public static void main(String[] args) throws Exception, IOException, NoSuchAlgorithmException {
        endpoint = System.getenv("SERVER_ENDPOINT");
        accessKey = System.getenv("ACCESS_KEY");
        secretKey = System.getenv("SECRET_KEY");
        enableHTTPS = System.getenv("ENABLE_HTTPS").equals("1");
        enableHTTPTests = "1".equals(System.getenv("ENABLE_HTTP_TESTS"));

        region = Region.US_EAST_1;

        if (enableHTTPS) {
            endpoint = "https://" + endpoint;
        } else {
            endpoint = "http://" + endpoint;
        }

        String dataDir = System.getenv("MINT_DATA_DIR");
        if (dataDir != null && !dataDir.equals("")) {
            mintEnv = true;
            file1Kb = Paths.get(dataDir, "datafile-1-kB").toString();
            file1Mb = Paths.get(dataDir, "datafile-1-MB").toString();
            file6Mb = Paths.get(dataDir, "datafile-6-MB").toString();
        }

        String mintMode = null;
        if (mintEnv) {
            mintMode = System.getenv("MINT_MODE");
        }
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);
        if (enableHTTPS) {
            SdkHttpClient sdkHttpClient = new DefaultSdkHttpClientBuilder().buildWithDefaults(
                    AttributeMap
                            .builder()
                            .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
                            .build());
            s3Client = S3Client
                    .builder()
                    .endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(region)
                    .httpClient(sdkHttpClient)
                    .build();
            SdkAsyncHttpClient sdkAsyncHttpClient = NettyNioAsyncHttpClient
                    .builder()
                    .buildWithDefaults(AttributeMap
                            .builder()
                            .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
                            .build());
            s3AsyncClient = S3AsyncClient
                    .builder()
                    .endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(region)
                    .httpClient(sdkAsyncHttpClient)
                    .build();
            SdkAsyncHttpClient crtAsyncHttpClient = AwsCrtAsyncHttpClient
                    .builder()
                    .buildWithDefaults(AttributeMap
                            .builder()
                            .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, true)
                            .build());
	    s3CrtAsyncClient = S3AsyncClient
		    .builder()
                    .endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(region)
		    .httpClient(crtAsyncHttpClient)
		    .build();
        } else {
            s3Client = S3Client
                    .builder()
                    .endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(region)
                    .build();
	    S3Configuration configuration = S3Configuration.builder()
				.chunkedEncodingEnabled(true)
				.build();
            s3AsyncClient = S3AsyncClient
                    .builder()
                    .endpointOverride(URI.create(endpoint))
		    .serviceConfiguration(configuration)
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(region)
                    .build();
	    s3CrtAsyncClient = S3AsyncClient
		    .crtBuilder()
		    .checksumValidationEnabled(false)
		    .endpointOverride(URI.create(endpoint))
                    .forcePathStyle(true)
                    .credentialsProvider(StaticCredentialsProvider.create(credentials))
                    .region(region)
		    .build();
        }

        s3TestUtils = new S3TestUtils(s3Client, s3AsyncClient, s3CrtAsyncClient);

        try {
            initTests();
            FunctionalTests.runTests();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        } finally {
            teardown();
        }
    }
}
