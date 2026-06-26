package com.vaspshow.backend.dto;

import java.util.List;

public record FileFieldProfileResponse(
    String name,
    String type,
    long filled,
    long sampled,
    String mappedField,
    List<String> examples
) {
}
