package dev.higor.trackflow.api.ableton.web;

import dev.higor.trackflow.api.ableton.application.AbletonQueryService;
import dev.higor.trackflow.api.identity.AuthenticatedUser;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{projectId}/versions/{versionId}/ableton")
class AbletonController {

    private final AbletonQueryService service;

    AbletonController(AbletonQueryService service) {
        this.service = service;
    }

    @GetMapping
    AnalysisResponse get(@PathVariable UUID projectId, @PathVariable UUID versionId, AuthenticatedUser user) {
        return AnalysisResponse.from(service.find(projectId, versionId, user.id()));
    }

    /** {@code GET .../versions/{v4}/ableton/diff?against={v3}} lists what changed from v3 to v4. */
    @GetMapping("/diff")
    DiffResponse diff(
            @PathVariable UUID projectId,
            @PathVariable UUID versionId,
            @RequestParam UUID against,
            AuthenticatedUser user) {
        return DiffResponse.from(against, versionId, service.diff(projectId, versionId, against, user.id()));
    }
}
