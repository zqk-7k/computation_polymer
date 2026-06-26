package com.vaspshow.backend.dto;

public record AtomCoordinateResponse(
    int index,
    String element,
    double x,
    double y,
    double z
) {
}
