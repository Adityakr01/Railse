package com.railse.hiring.workforcemgmt.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;

import java.util.List;

@Data
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TaskFetchByDateRequest {
private Long startDate;
private Long endDate;
private List<Long> assigneeIds;
}


@Service
public class TaskManagementServiceImpl implements TaskManagementService {

    private final TaskRepository taskRepository;
    private final ITaskManagementMapper taskMapper;

    // ... constructor and other methods

    @Override
    public List<TaskManagementDto> fetchTasksByDate(TaskFetchByDateRequest request) {
        List<TaskManagement> tasks = taskRepository.findByAssigneeIdIn(request.getAssigneeIds());

        List<TaskManagement> filteredTasks = tasks.stream()
                .filter(task -> {
                    // --- BUG #2 FIX: Start ---
                    // Exclude tasks that have been CANCELLED
                    if (task.getStatus() == TaskStatus.CANCELLED) {
                        return false;
                    }
                    // --- BUG #2 FIX: End ---

                    // This logic is incomplete for the assignment.
                    // It should check against startDate and endDate.
                    // For now, it just returns all tasks for the assignees.
                    return true;
                })
                .collect(Collectors.toList());

        return taskMapper.modelListToDtoList(filteredTasks);
    }
}