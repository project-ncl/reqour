/*
 * SPDX-FileCopyrightText: Copyright © 2024 Red Hat, Inc.
 * SPDX-License-Identifier: Apache-2.0
 */
package org.jboss.pnc.reqour.rest.endpoints;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.including;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.jboss.pnc.reqour.rest.endpoints.TestConstants.TEST_USER;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.jboss.pnc.api.reqour.rest.ScmContentEndpoint;
import org.jboss.pnc.reqour.service.scmcontent.ScmContentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.wiremock.devservice.ConnectWireMock;
import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.RestAssured;
import io.restassured.response.Response;

/**
 * GitLab is the enabled provider in the test configuration and GitHub is not; both are served.
 */
@QuarkusTest
@TestHTTPEndpoint(ScmContentEndpoint.class)
@ConnectWireMock
@TestSecurity(user = TEST_USER, roles = { OidcRoleConstants.PNC_APP_REPOUR_USER })
class ScmContentEndpointTest {

    private static final String GITLAB_URL = "git@localhost:test-workspace/group/repo.git";
    private static final String GITLAB_PROJECT = "/api/v4/projects/test-workspace%2Fgroup%2Frepo";
    private static final String GITHUB_URL = "https://localhost/test-organization/repo.git";
    private static final String GITHUB_REPO = "/repos/test-organization/repo";
    private static final String BASE = "1111111111111111111111111111111111111111";
    private static final String HEAD = "2222222222222222222222222222222222222222";

    WireMock wireMock;

    @AfterEach
    void tearDown() {
        wireMock.resetMappings();
        wireMock.resetRequests();
    }

    @Test
    void gitlabArchive_triesTheRevisionsInOrder() {
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/archive.tar.gz"))
                        .withQueryParam("sha", equalTo("missing"))
                        .willReturn(aResponse().withStatus(404).withBody("{\"message\":\"404 Not Found\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/archive.tar.gz")).withQueryParam("sha", equalTo(HEAD))
                        .withHeader("PRIVATE-TOKEN", equalTo("I don't even need to be real"))
                        .willReturn(aResponse().withStatus(200).withBody(new byte[] { 0x1f, (byte) 0x8b, 1, 2 })));

        Response response = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("revision", "missing", HEAD)
                .get("/archive");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header(ScmContentEndpoint.REVISION_HEADER)).isEqualTo(HEAD);
        assertThat(response.asByteArray()).containsExactly(0x1f, 0x8b, 1, 2);
    }

    @Test
    void gitlabDiff_comparesAndFillsLeftOutFiles() {
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/merge_base"))
                        .willReturn(okJson("{\"id\":\"" + BASE + "\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/commits/" + BASE))
                        .willReturn(okJson("{\"id\":\"" + BASE + "\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/compare")).withQueryParam("straight", equalTo("true"))
                        .willReturn(
                                okJson(
                                        "{\"diffs\":["
                                                + "{\"old_path\":\"pom.xml\",\"new_path\":\"pom.xml\",\"a_mode\":\"100644\","
                                                + "\"b_mode\":\"100644\",\"diff\":\"@@ -1 +1 @@\\n-<v>1</v>\\n+<v>1.redhat-00001</v>\\n\"},"
                                                + "{\"old_path\":\"Big.java\",\"new_path\":\"Big.java\",\"a_mode\":\"0\","
                                                + "\"b_mode\":\"100644\",\"new_file\":true,\"too_large\":true,\"diff\":\"\"}"
                                                + "]}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/files/Big.java/raw"))
                        .withQueryParam("ref", equalTo(HEAD))
                        .willReturn(aResponse().withStatus(200).withBody("class Big {}\n")));

        Response response = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header(ScmContentEndpoint.DIFF_MODE_HEADER))
                .isEqualTo(ScmContentEndpoint.DIFF_MODE_COMPARE);
        assertThat(response.header(ScmContentEndpoint.DIFF_BASE_HEADER)).isEqualTo(BASE);
        assertThat(response.header(ScmContentEndpoint.DIFF_HEAD_HEADER)).isEqualTo(HEAD);
        assertThat(new String(response.asByteArray(), StandardCharsets.UTF_8)).isEqualTo(
                "diff --git a/pom.xml b/pom.xml\n--- a/pom.xml\n+++ b/pom.xml\n@@ -1 +1 @@\n-<v>1</v>\n"
                        + "+<v>1.redhat-00001</v>\n" + "diff --git a/Big.java b/Big.java\nnew file mode 100644\n"
                        + "--- /dev/null\n+++ b/Big.java\n@@ -0,0 +1 @@\n+class Big {}\n");
    }

    @Test
    void gitlabDiff_fallsBackToTheHeadCommitWithoutCommonAncestor() {
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/merge_base"))
                        .willReturn(
                                aResponse().withStatus(404).withBody("{\"message\":\"404 Merge Base Not Found\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/commits/" + HEAD))
                        .willReturn(okJson("{\"id\":\"" + HEAD + "\",\"parent_ids\":[\"" + BASE + "\"]}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/commits/" + HEAD + "/diff")).willReturn(
                        okJson(
                                "[{\"old_path\":\"a.txt\",\"new_path\":\"a.txt\",\"a_mode\":\"100644\",\"b_mode\":\"100644\","
                                        + "\"diff\":\"@@ -1 +1 @@\\n-a\\n+b\\n\"}]")));

        Response response = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header(ScmContentEndpoint.DIFF_MODE_HEADER)).isEqualTo(ScmContentEndpoint.DIFF_MODE_COMMIT);
        assertThat(response.asString()).isEqualTo(
                "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n-a\n+b\n");
    }

    @Test
    void gitlabDiff_fallsBackToTheHeadCommitWhenBaseIsNotAnAncestor() {
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/merge_base"))
                        .willReturn(okJson("{\"id\":\"3333333333333333333333333333333333333333\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/commits/" + BASE))
                        .willReturn(okJson("{\"id\":\"" + BASE + "\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/commits/" + HEAD))
                        .willReturn(okJson("{\"id\":\"" + HEAD + "\",\"parent_ids\":[\"" + BASE + "\"]}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/commits/" + HEAD + "/diff")).willReturn(
                        okJson(
                                "[{\"old_path\":\"a.txt\",\"new_path\":\"a.txt\",\"a_mode\":\"100644\",\"b_mode\":\"100644\","
                                        + "\"diff\":\"@@ -1 +1 @@\\n-a\\n+b\\n\"}]")));

        Response response = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header(ScmContentEndpoint.DIFF_MODE_HEADER)).isEqualTo(ScmContentEndpoint.DIFF_MODE_COMMIT);
        assertThat(response.header(ScmContentEndpoint.DIFF_BASE_HEADER)).isNull();
        assertThat(response.asString()).contains("+b\n");
    }

    @Test
    void githubDiff_fillsBinaryFiles() {
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/compare/" + BASE + "..." + HEAD))
                        .withHeader("Accept", equalTo("application/vnd.github+json"))
                        .willReturn(okJson("{\"status\":\"ahead\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/compare/" + BASE + "..." + HEAD))
                        .withHeader("Accept", equalTo("application/vnd.github.diff"))
                        .withHeader("Authorization", equalTo("Bearer I don't even need to be real"))
                        .willReturn(
                                aResponse().withStatus(200)
                                        .withBody(
                                                "diff --git a/lib/x.bin b/lib/x.bin\nnew file mode 100644\n"
                                                        + "index 0000000..1234567\n"
                                                        + "Binary files /dev/null and b/lib/x.bin differ\n")));
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/contents/lib/x.bin")).withQueryParam("ref", equalTo(HEAD))
                        .willReturn(aResponse().withStatus(200).withBody(new byte[] { 0, 1, 2 })));

        Response response = RestAssured.given()
                .queryParam("url", GITHUB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(
                "diff --git a/lib/x.bin b/lib/x.bin\nnew file mode 100644\n"
                        + "index 0000000000000000000000000000000000000000..2a1ae0d7fe7bdf3e81e2e9a4c4b4b4d9c2d0f6e1\n"
                                .replace("2a1ae0d7fe7bdf3e81e2e9a4c4b4b4d9c2d0f6e1", blobId(new byte[] { 0, 1, 2 }))
                        + "GIT binary patch\nliteral 3\nKcmZQzWC8#H2LJ>B\n\nliteral 0\nHcmV?d00001\n\n");
    }

    @Test
    void githubDiff_keepsTheBytesOfFilesThatAreNotUtf8() {
        // ISO-8859-1 content: café -> café crème
        byte[] diff = ("diff --git a/latin1.txt b/latin1.txt\nindex 1111111..2222222 100644\n"
                + "--- a/latin1.txt\n+++ b/latin1.txt\n@@ -1 +1 @@\n-caf\u00e9\n+caf\u00e9 cr\u00e8me\n")
                .getBytes(StandardCharsets.ISO_8859_1);
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/compare/" + BASE + "..." + HEAD))
                        .withHeader("Accept", equalTo("application/vnd.github+json"))
                        .willReturn(okJson("{\"status\":\"ahead\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/compare/" + BASE + "..." + HEAD))
                        .withHeader("Accept", equalTo("application/vnd.github.diff"))
                        .willReturn(aResponse().withStatus(200).withBody(diff)));

        Response response = RestAssured.given()
                .queryParam("url", GITHUB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asByteArray()).isEqualTo(diff);
    }

    @Test
    void diffsOverTheConcurrencyLimitAreRefusedWithRetryAfter() throws Exception {
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/compare/" + BASE + "..." + HEAD))
                        .withHeader("Accept", equalTo("application/vnd.github+json"))
                        .willReturn(okJson("{\"status\":\"ahead\"}").withFixedDelay(3000)));
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/compare/" + BASE + "..." + HEAD))
                        .withHeader("Accept", equalTo("application/vnd.github.diff"))
                        .willReturn(aResponse().withStatus(200).withBody("")));
        ExecutorService executor = Executors.newFixedThreadPool(ScmContentService.MAX_CONCURRENT_DIFFS);
        try {
            List<Future<Integer>> running = new ArrayList<>();
            for (int i = 0; i < ScmContentService.MAX_CONCURRENT_DIFFS; i++) {
                running.add(executor.submit(() -> diffRequest().statusCode()));
            }
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (wireMock.find(getRequestedFor(urlPathEqualTo(GITHUB_REPO + "/compare/" + BASE + "..." + HEAD)))
                    .size() < ScmContentService.MAX_CONCURRENT_DIFFS && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }

            Response refused = diffRequest();

            assertThat(refused.statusCode()).isEqualTo(503);
            assertThat(refused.header("Retry-After"))
                    .isEqualTo(String.valueOf(ScmContentService.BUSY_RETRY_AFTER_SECONDS));
            assertThat(refused.jsonPath().getString("errorType")).isEqualTo("SERVICE_UNAVAILABLE");
            for (Future<Integer> request : running) {
                assertThat(request.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            executor.shutdownNow();
        }
        // the permits are given back
        assertThat(diffRequest().statusCode()).isEqualTo(200);
    }

    private static Response diffRequest() {
        return RestAssured.given()
                .queryParam("url", GITHUB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");
    }

    @Test
    void githubDiff_rebuildsTooLargeDiffsFromTheFileList() {
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/compare/" + BASE + "..." + HEAD))
                        .withHeader("Accept", equalTo("application/vnd.github+json"))
                        .willReturn(
                                okJson(
                                        "{\"status\":\"ahead\",\"files\":[{\"filename\":\"gradlew\",\"status\":\"modified\","
                                                + "\"changes\":2,\"patch\":\"@@ -1 +1 @@\\n-a\\n+b\"}]}")));
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/compare/" + BASE + "..." + HEAD))
                        .withHeader("Accept", equalTo("application/vnd.github.diff"))
                        .willReturn(aResponse().withStatus(406).withBody("{\"message\":\"diff too large\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/git/trees/" + BASE))
                        .willReturn(okJson("{\"tree\":[{\"path\":\"gradlew\",\"mode\":\"100644\"}]}")));
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/git/trees/" + HEAD))
                        .willReturn(okJson("{\"tree\":[{\"path\":\"gradlew\",\"mode\":\"100755\"}]}")));

        Response response = RestAssured.given()
                .queryParam("url", GITHUB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asString()).isEqualTo(
                "diff --git a/gradlew b/gradlew\nold mode 100644\nnew mode 100755\n--- a/gradlew\n+++ b/gradlew\n"
                        + "@@ -1 +1 @@\n-a\n+b\n");
    }

    @Test
    void githubArchive_followsTheRedirectWithoutTheToken() {
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/tarball/" + HEAD))
                        .willReturn(
                                aResponse().withStatus(302).withHeader("Location", "/codeload/repo.tar.gz?token=x")));
        wireMock.register(
                get(urlPathEqualTo("/codeload/repo.tar.gz"))
                        .willReturn(aResponse().withStatus(200).withBody(new byte[] { 0x1f, (byte) 0x8b })));

        Response response = RestAssured.given()
                .queryParam("url", GITHUB_URL)
                .queryParam("revision", HEAD)
                .get("/archive");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.asByteArray()).containsExactly(0x1f, 0x8b);
        wireMock.verifyThat(
                getRequestedFor(urlPathEqualTo(GITHUB_REPO + "/tarball/" + HEAD))
                        .withHeader("Authorization", equalTo("Bearer I don't even need to be real")));
    }

    @Test
    void repositoriesOutsideTheInternalLocationsAreRejected() {
        for (String url : new String[] {
                "https://localhost/other-group/repo.git",
                "https://example.com/test-workspace/repo.git",
                "https://localhost/test-workspace/../other/repo.git",
                "" }) {
            Response response = RestAssured.given().queryParam("url", url).queryParam("revision", HEAD).get("/archive");
            assertThat(response.statusCode()).as(url).isEqualTo(400);
        }
    }

    @Test
    void gitlabAnswersWithoutDiffsAreProviderErrors() {
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/merge_base"))
                        .willReturn(okJson("{\"id\":\"" + BASE + "\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/commits/" + BASE))
                        .willReturn(okJson("{\"id\":\"" + BASE + "\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/compare")).willReturn(okJson("{\"commits\":[]}")));

        Response response = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.asString()).contains("unexpected answer").contains("BAD_GATEWAY");
    }

    @Test
    void providerAuthErrorsAreReported() {
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/merge_base"))
                        .willReturn(aResponse().withStatus(401).withBody("{\"message\":\"401 Unauthorized\"}")));

        Response response = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.asString()).contains("The git provider returned HTTP 401")
                .contains("BAD_GATEWAY")
                .doesNotContain("Unauthorized");
    }

    @Test
    void gitlabUnknownRefsMoveOnToTheNextBase() {
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/merge_base"))
                        .withQueryParam("refs[]", including("missing"))
                        .atPriority(1)
                        .willReturn(
                                aResponse().withStatus(400).withBody("{\"message\":\"Could not find ref: missing\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/merge_base")).atPriority(5)
                        .willReturn(okJson("{\"id\":\"" + BASE + "\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/commits/" + BASE))
                        .willReturn(okJson("{\"id\":\"" + BASE + "\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/compare")).willReturn(okJson("{\"diffs\":[]}")));

        Response response = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("base", "missing", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header(ScmContentEndpoint.DIFF_BASE_HEADER)).isEqualTo(BASE);
    }

    @Test
    void otherGitlabBadRequestsAreProviderErrors() {
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/merge_base")).willReturn(
                        aResponse().withStatus(400).withBody("{\"message\":\"400 Bad request - refs is invalid\"}")));

        Response response = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.asString()).doesNotContain("refs is invalid");
    }

    @Test
    void gitlabComparisonsAtTheFileLimitAreRejected() {
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/merge_base"))
                        .willReturn(okJson("{\"id\":\"" + BASE + "\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/commits/" + BASE))
                        .willReturn(okJson("{\"id\":\"" + BASE + "\"}")));
        StringBuilder diffs = new StringBuilder("{\"diffs\":[");
        for (int i = 0; i < 1000; i++) {
            diffs.append(i == 0 ? "" : ",")
                    .append("{\"old_path\":\"f")
                    .append(i)
                    .append("\",\"new_path\":\"f")
                    .append(i)
                    .append("\",\"diff\":\"@@ -1 +1 @@\\n-a\\n+b\\n\"}");
        }
        wireMock.register(
                get(urlPathEqualTo(GITLAB_PROJECT + "/repository/compare"))
                        .willReturn(okJson(diffs.append("]}").toString())));

        Response response = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(502);
        assertThat(response.asString()).contains("too many files");
    }

    @Test
    void githubFallsBackToTheHeadCommitWithoutCommonAncestor() {
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/compare/" + BASE + "..." + HEAD))
                        .willReturn(aResponse().withStatus(404).withBody("{\"message\":\"Not Found\"}")));
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/commits/" + HEAD))
                        .withHeader("Accept", equalTo("application/vnd.github+json"))
                        .willReturn(okJson("{\"sha\":\"" + HEAD + "\",\"parents\":[{\"sha\":\"" + BASE + "\"}]}")));
        wireMock.register(
                get(urlPathEqualTo(GITHUB_REPO + "/commits/" + HEAD))
                        .withHeader("Accept", equalTo("application/vnd.github.diff"))
                        .willReturn(
                                aResponse().withStatus(200)
                                        .withBody(
                                                "diff --git a/a.txt b/a.txt\n--- a/a.txt\n+++ b/a.txt\n@@ -1 +1 @@\n-a\n+b\n")));

        Response response = RestAssured.given()
                .queryParam("url", GITHUB_URL)
                .queryParam("base", BASE)
                .queryParam("head", HEAD)
                .get("/diff");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.header(ScmContentEndpoint.DIFF_MODE_HEADER)).isEqualTo(ScmContentEndpoint.DIFF_MODE_COMMIT);
        assertThat(response.asString()).contains("+b\n");
    }

    @Test
    void invalidPathsAreRejected() {
        for (String url : new String[] {
                "https://localhost/test-workspace/a%2F..%2Fb.git",
                "https://localhost/test-organization/a/b.git" }) {
            Response response = RestAssured.given().queryParam("url", url).queryParam("revision", HEAD).get("/archive");
            assertThat(response.statusCode()).as(url).isEqualTo(400);
            assertThat(response.asString()).contains("BAD_REQUEST");
        }
    }

    @Test
    void tooManyCandidatesAreRejected() {
        Object[] revisions = new Object[11];
        for (int i = 0; i < revisions.length; i++) {
            revisions[i] = "r" + i;
        }
        Response response = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("revision", revisions)
                .get("/archive");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.asString()).contains("At most 10 revision values");
    }

    @Test
    void notServedRepositoriesDoNotListTheServedLocations() {
        Response response = RestAssured.given()
                .queryParam("url", "https://localhost/other-group/repo.git")
                .queryParam("revision", HEAD)
                .get("/archive");

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.asString()).doesNotContain("test-workspace").doesNotContain("test-organization");
    }

    @Test
    void missingCandidatesAreRejected() {
        Response response = RestAssured.given().queryParam("url", GITLAB_URL).queryParam("head", HEAD).get("/diff");
        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.asString()).contains("base");
    }

    @Test
    @TestSecurity(user = TEST_USER, roles = { OidcRoleConstants.PNC_APP_ORCH_BUILD_READ })
    void orchBuildReadersAreAllowed() {
        assertPastTheSecurityCheck();
    }

    @Test
    @TestSecurity(user = TEST_USER, roles = { OidcRoleConstants.PNC_USERS })
    void pncUsersAreAllowed() {
        assertPastTheSecurityCheck();
    }

    @Test
    @TestSecurity(user = TEST_USER, roles = { OidcRoleConstants.PNC_USERS_ADMIN })
    void pncAdminsAreAllowed() {
        assertPastTheSecurityCheck();
    }

    private static void assertPastTheSecurityCheck() {
        // 400 from validation: past the security check
        Response archive = RestAssured.given().queryParam("url", "").queryParam("revision", HEAD).get("/archive");
        assertThat(archive.statusCode()).isEqualTo(400);
        Response diff = RestAssured.given().queryParam("url", GITLAB_URL).queryParam("head", HEAD).get("/diff");
        assertThat(diff.statusCode()).isEqualTo(400);
    }

    @Test
    @TestSecurity(user = TEST_USER, roles = { "someone-else" })
    void otherRolesAreForbidden() {
        Response archive = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("revision", HEAD)
                .get("/archive");
        assertThat(archive.statusCode()).isEqualTo(403);
        Response diff = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("base", HEAD)
                .queryParam("head", HEAD)
                .get("/diff");
        assertThat(diff.statusCode()).isEqualTo(403);
    }

    @Test
    @TestSecurity
    void anonymousCallersAreRejected() {
        Response archive = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("revision", HEAD)
                .get("/archive");
        assertThat(archive.statusCode()).isEqualTo(401);
        Response diff = RestAssured.given()
                .queryParam("url", GITLAB_URL)
                .queryParam("base", HEAD)
                .queryParam("head", HEAD)
                .get("/diff");
        assertThat(diff.statusCode()).isEqualTo(401);
    }

    private static String blobId(byte[] content) {
        return org.jboss.pnc.common.scm.diff.GitPatchContent.blobId(content);
    }
}
