class ContentViolationDetected {
  constructor({
    violationId,
    requestId,
    sourceService,
    contentType,
    contentRefId,
    userId,
    tenantId,
    severity,
    violationType,
    matchedLabels,
    evidence,
    detectedAt,
  }) {
    this.violationId = violationId;
    this.requestId = requestId;
    this.sourceService = sourceService;
    this.contentType = contentType;
    this.contentRefId = contentRefId;
    this.userId = userId;
    this.tenantId = tenantId;
    this.severity = severity;
    this.violationType = violationType;
    this.matchedLabels = Array.isArray(matchedLabels) ? matchedLabels : [];
    this.evidence = evidence || {};
    this.detectedAt = detectedAt || new Date().toISOString();
  }

  static fromPayload(payload) {
    if (!payload || typeof payload !== "object") {
      throw new Error("Content violation payload must be an object");
    }

    const event = new ContentViolationDetected(payload);
    event.validateEnvelope();
    return event;
  }

  validateEnvelope() {
    if (!this.violationId || typeof this.violationId !== "string") {
      throw new Error("violationId is required");
    }

    if (!this.requestId || typeof this.requestId !== "string") {
      throw new Error("requestId is required");
    }

    if (!this.sourceService || typeof this.sourceService !== "string") {
      throw new Error("sourceService is required");
    }

    if (!this.contentType || typeof this.contentType !== "string") {
      throw new Error("contentType is required");
    }

    if (!this.contentRefId || typeof this.contentRefId !== "string") {
      throw new Error("contentRefId is required");
    }
  }

  isChatTextViolation() {
    return this.sourceService === "chat-service" && this.contentType === "TEXT";
  }

  isChatImageViolation() {
    return this.sourceService === "chat-service" && this.contentType === "IMAGE";
  }
}

module.exports = ContentViolationDetected;
