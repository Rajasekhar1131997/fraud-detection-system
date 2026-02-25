package com.frauddetection.fraudservice.dto;

import java.util.List;

public record DashboardDecisionPageResponse(
        List<DashboardDecisionDto> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
}
