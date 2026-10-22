package dev.higor.trackflow.api.version.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.higor.trackflow.api.identity.domain.User;
import dev.higor.trackflow.api.shared.error.BusinessRuleException;
import dev.higor.trackflow.api.storage.SignedRequest;
import dev.higor.trackflow.api.support.Fixtures;
import dev.higor.trackflow.api.support.IntegrationTest;
import dev.higor.trackflow.api.support.SeaweedFsTestConfiguration;
import dev.higor.trackflow.api.version.VersionConfirmed;
import dev.higor.trackflow.api.version.VersionStatus;
import dev.higor.trackflow.api.version.domain.TrackVersionRepository;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/** The whole confirmation against PostgreSQL and SeaweedFS, with a real upload in between. */
@IntegrationTest
@Import(Fixtures.class)
@RecordApplicationEvents
class VersionConfirmationIT {

    private static final int SIZE = 4096;

    private final HttpClient http = HttpClient.newHttpClient();

    @Autowired
    private VersionService service;

    @Autowired
    private TrackVersionRepository versions;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private ApplicationEvents events;

    @Autowired
    private S3Client s3;

    private User editor;
    private UUID projectId;

    @BeforeEach
    void setUp() {
        editor = fixtures.user("editor");
        projectId = fixtures.projectOwnedBy(editor);
    }

    @Test
    void confirmsUploadedFileWithTheFirstNumberAndPublishesTheEvent() throws Exception {
        UploadTicket ticket = requestUpload();
        upload(ticket.source(), new byte[SIZE]);

        VersionView confirmed = service.confirm(projectId, ticket.version().id(), editor.getId());

        assertThat(confirmed.number()).isEqualTo(1);
        assertThat(confirmed.status()).isEqualTo(VersionStatus.UPLOADED);
        assertThat(events.stream(VersionConfirmed.class))
                .singleElement()
                .satisfies(event -> assertThat(event.versionId()).isEqualTo(confirmed.id()));
    }

    @Test
    void refusesConfirmationWhileTheFileIsMissing() {
        UploadTicket ticket = requestUpload();

        assertThatThrownBy(() -> service.confirm(projectId, ticket.version().id(), editor.getId()))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("upload-missing"));
        assertThat(versions.findById(ticket.version().id())).hasValueSatisfying(version -> {
            assertThat(version.getStatus()).isEqualTo(VersionStatus.PENDING_UPLOAD);
            assertThat(version.getNumber()).isNull();
        });
    }

    @Test
    void refusesFileWithAnotherSizeThanDeclared() {
        UploadTicket ticket = requestUpload();
        String key = versions.findById(ticket.version().id()).orElseThrow().getSourceKey();
        // The signed URL cannot produce this; write a shorter object with the storage credentials.
        s3.putObject(
                put -> put.bucket(SeaweedFsTestConfiguration.BUCKET).key(key), RequestBody.fromBytes(new byte[10]));

        assertThatThrownBy(() -> service.confirm(projectId, ticket.version().id(), editor.getId()))
                .isInstanceOfSatisfying(
                        BusinessRuleException.class,
                        error -> assertThat(error.code()).isEqualTo("upload-size-mismatch"));
        assertThat(versions.findById(ticket.version().id()).orElseThrow().getNumber())
                .isNull();
    }

    @Test
    void repeatedConfirmationKeepsTheNumberAndPublishesOnce() throws Exception {
        UploadTicket ticket = requestUpload();
        upload(ticket.source(), new byte[SIZE]);
        service.confirm(projectId, ticket.version().id(), editor.getId());

        VersionView again = service.confirm(projectId, ticket.version().id(), editor.getId());

        assertThat(again.number()).isEqualTo(1);
        assertThat(events.stream(VersionConfirmed.class)).hasSize(1);
    }

    private UploadTicket requestUpload() {
        return service.requestUpload(
                new RequestUpload(projectId, editor.getId(), "bounce.wav", "audio/wav", SIZE, null, null, null));
    }

    private void upload(SignedRequest signed, byte[] content) throws Exception {
        HttpRequest.Builder request =
                HttpRequest.newBuilder(signed.url()).PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        signed.headers().forEach((name, value) -> {
            if (!name.equalsIgnoreCase("content-length")) {
                request.header(name, value);
            }
        });
        HttpResponse<Void> response = http.send(request.build(), HttpResponse.BodyHandlers.discarding());
        assertThat(response.statusCode()).isEqualTo(200);
    }
}
