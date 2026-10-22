package dev.higor.trackflow.api.version.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.higor.trackflow.api.identity.domain.User;
import dev.higor.trackflow.api.support.Fixtures;
import dev.higor.trackflow.api.support.IntegrationTest;
import dev.higor.trackflow.api.support.SeaweedFsTestConfiguration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Two confirmations of the same project released at the same instant. The counter row lock makes
 * one wait for the other, so both succeed with consecutive numbers (design D5).
 */
@IntegrationTest
@Import(Fixtures.class)
class VersionNumberingConcurrencyIT {

    private static final int SIZE = 1024;

    @Autowired
    private VersionService service;

    @Autowired
    private Fixtures fixtures;

    @Autowired
    private S3Client s3;

    @Test
    void simultaneousConfirmationsGetTheNextTwoNumbers() throws Exception {
        User editor = fixtures.user("concurrency");
        UUID projectId = fixtures.projectOwnedBy(editor);
        for (int i = 0; i < 3; i++) {
            service.confirm(projectId, uploaded(projectId, editor), editor.getId());
        }
        UUID first = uploaded(projectId, editor);
        UUID second = uploaded(projectId, editor);
        CountDownLatch start = new CountDownLatch(1);

        List<Integer> numbers = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            Future<VersionView> a = pool.submit(() -> {
                start.await();
                return service.confirm(projectId, first, editor.getId());
            });
            Future<VersionView> b = pool.submit(() -> {
                start.await();
                return service.confirm(projectId, second, editor.getId());
            });
            start.countDown();
            numbers.add(a.get().number());
            numbers.add(b.get().number());
        }

        assertThat(numbers).containsExactlyInAnyOrder(4, 5);
    }

    /** A pending version whose file is already in the storage, ready to be confirmed. */
    private UUID uploaded(UUID projectId, User editor) {
        UploadTicket ticket = service.requestUpload(
                new RequestUpload(projectId, editor.getId(), "bounce.wav", "audio/wav", SIZE, null, null, null));
        String key = ticket.source().url().getPath().replaceFirst("^/" + SeaweedFsTestConfiguration.BUCKET + "/", "");
        s3.putObject(
                put -> put.bucket(SeaweedFsTestConfiguration.BUCKET).key(key).contentType("audio/wav"),
                RequestBody.fromBytes(new byte[SIZE]));
        return ticket.version().id();
    }
}
