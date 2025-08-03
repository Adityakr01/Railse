package com.railse.hiring.workforcemgmt.model;

import com.railse.hiring.workforcemgmt.model.enums.TaskActivityEventType;
import lombok.Data;

@Data
public class TaskActivity {
    private Long id;
    private Long taskId;
    private Long timestamp;
    private TaskActivityEventType eventType;
    private String message;
    private Long userId; // The user who performed the action
}