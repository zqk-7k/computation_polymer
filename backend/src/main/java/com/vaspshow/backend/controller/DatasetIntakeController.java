package com.vaspshow.backend.controller;

import com.vaspshow.backend.dto.DatasetSubmissionRequest;
import com.vaspshow.backend.dto.DatasetSubmissionResponse;
import com.vaspshow.backend.dto.DiscoveryCandidateResponse;
import com.vaspshow.backend.dto.DiscoveryConfigResponse;
import com.vaspshow.backend.dto.DiscoveryConfigUpdateRequest;
import com.vaspshow.backend.dto.DiscoveryDecisionRequest;
import com.vaspshow.backend.dto.DiscoveryRunResponse;
import com.vaspshow.backend.dto.DiscoverySourceResponse;
import com.vaspshow.backend.dto.IngestRequest;
import com.vaspshow.backend.dto.IngestSuggestionResponse;
import com.vaspshow.backend.dto.PublicDatasetSourceResponse;
import com.vaspshow.backend.dto.SubmissionReviewRequest;
import com.vaspshow.backend.service.DatasetDiscoveryService;
import com.vaspshow.backend.service.DatasetIntakeService;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/intake")
public class DatasetIntakeController {

  private final DatasetIntakeService intakeService;
  private final DatasetDiscoveryService discoveryService;

  public DatasetIntakeController(DatasetIntakeService intakeService, DatasetDiscoveryService discoveryService) {
    this.intakeService = intakeService;
    this.discoveryService = discoveryService;
  }

  @GetMapping("/sources")
  public List<PublicDatasetSourceResponse> sources() {
    return intakeService.listPublicSources();
  }

  @GetMapping("/discovery/sources")
  public List<DiscoverySourceResponse> discoverySources() {
    return discoveryService.listSources();
  }

  @GetMapping("/discovery/config")
  public DiscoveryConfigResponse discoveryConfig(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    return discoveryService.getConfig(authorization);
  }

  @PutMapping("/discovery/config")
  public DiscoveryConfigResponse updateDiscoveryConfig(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody DiscoveryConfigUpdateRequest request
  ) {
    return discoveryService.updateConfig(authorization, request);
  }

  @GetMapping("/discovery/runs")
  public List<DiscoveryRunResponse> discoveryRuns(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    return discoveryService.listRuns(authorization);
  }

  @PostMapping("/discovery/run")
  public DiscoveryRunResponse runDiscovery(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    return discoveryService.runDiscovery(authorization);
  }

  @GetMapping("/discovery/candidates")
  public List<DiscoveryCandidateResponse> discoveryCandidates(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    return discoveryService.listCandidates(authorization);
  }

  @PatchMapping("/discovery/candidates/{id}/review")
  public DiscoveryCandidateResponse reviewCandidate(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable long id,
      @RequestBody DiscoveryDecisionRequest request
  ) {
    return discoveryService.reviewCandidate(authorization, id, request);
  }

  @PostMapping("/discovery/candidates/{id}/promote")
  public DatasetSubmissionResponse promoteCandidate(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable long id
  ) {
    return discoveryService.promoteCandidate(authorization, id);
  }

  @PostMapping("/discovery/candidates/{id}/validate")
  public DiscoveryCandidateResponse validateCandidate(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable long id
  ) {
    return discoveryService.validateCandidate(authorization, id);
  }

  @GetMapping("/submissions")
  public List<DatasetSubmissionResponse> submissions(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization
  ) {
    return intakeService.listSubmissions(authorization);
  }

  @PostMapping("/submissions")
  public DatasetSubmissionResponse submit(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @RequestBody DatasetSubmissionRequest request
  ) {
    return intakeService.submit(authorization, request);
  }

  @PatchMapping("/submissions/{id}/review")
  public DatasetSubmissionResponse review(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable long id,
      @RequestBody SubmissionReviewRequest request
  ) {
    return intakeService.review(authorization, id, request);
  }

  @PostMapping("/submissions/{id}/prepare")
  public DatasetSubmissionResponse preparePipeline(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable long id
  ) {
    return intakeService.preparePipeline(authorization, id);
  }

  @PostMapping("/submissions/{id}/adapt")
  public DatasetSubmissionResponse adapt(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable long id
  ) {
    return intakeService.profileSubmission(authorization, id);
  }

  @GetMapping("/submissions/{id}/ingest-suggestion")
  public IngestSuggestionResponse ingestSuggestion(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable long id
  ) {
    return intakeService.ingestSuggestion(authorization, id);
  }

  @PostMapping("/submissions/{id}/ingest")
  public DatasetSubmissionResponse ingest(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable long id,
      @RequestBody IngestRequest request
  ) {
    return intakeService.ingestSubmission(authorization, id, request);
  }

  @PostMapping("/submissions/{id}/withdraw")
  public DatasetSubmissionResponse withdraw(
      @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
      @PathVariable long id
  ) {
    return intakeService.withdrawIngest(authorization, id);
  }
}
