package com.railse.hiring.workforcemgmt.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.railse.hiring.workforcemgmt.model.enums.TaskActivityEventType;
import lombok.Data;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TaskActivityDto {
    private Long id;
    private Long taskId;
    private Long timestamp;
    private TaskActivityEventType eventType;
    private String message;
    private Long userId;
}