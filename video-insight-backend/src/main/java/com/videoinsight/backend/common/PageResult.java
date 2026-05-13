package com.videoinsight.backend.common;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Paginated result wrapper")
public class PageResult<T> {

    @Schema(description = "Total number of records.")
    private long total;

    @Schema(description = "Current page number, 1-based.")
    private int page;

    @Schema(description = "Page size.")
    private int pageSize;

    @Schema(description = "Records on this page.")
    private List<T> records;
}
