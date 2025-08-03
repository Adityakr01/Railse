package com.railse.hiring.workforcemgmt.service.impl;

import com.railse.hiring.workforcemgmt.common.exception.ResourceNotFoundException;
import com.railse.hiring.workforcemgmt.dto.*;
import com.railse.hiring.workforcemgmt.mapper.ITaskManagementMapper;
import com.railse.hiring.workforcemgmt.model.TaskActivity;
import com.railse.hiring.workforcemgmt.model.TaskManagement;
import com.railse.hiring.workforcemgmt.model.enums.Priority;
import com.railse.hiring.workforcemgmt.model.enums.Task;
import com.railse.hiring.workforcemgmt.model.enums.TaskActivityEventType;
import com.railse.hiring.workforcemgmt.model.enums.TaskStatus;
import com.railse.hiring.workforcemgmt.repository.TaskRepository;
import com.railse.hiring.workforcemgmt.service.TaskManagementService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TaskManagementServiceImpl implements TaskManagementService {

    private final TaskRepository taskRepository;
    private final ITaskManagementMapper taskMapper;

    public TaskManagementServiceImpl(TaskRepository taskRepository, ITaskManagementMapper taskMapper) {
        this.taskRepository = taskRepository;
        this.taskMapper = taskMapper;
    }

    @Override
    public TaskManagementDto findTaskById(Long id) {
        TaskManagement task = taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + id));
        TaskManagementDto taskDto = taskMapper.modelToDto(task);
        List<TaskActivity> activities = taskRepository.findActivitiesByTaskId(id);
        taskDto.setActivities(taskMapper.activityListToDtoList(activities));
        return taskDto;
    }

    @Override
    public List<TaskManagementDto> createTasks(TaskCreateRequest createRequest) {
        List<TaskManagement> createdTasks = new ArrayList<>();
        for (TaskCreateRequest.RequestItem item : createRequest.getRequests()) {
            TaskManagement newTask = new TaskManagement();
            newTask.setReferenceId(item.getReferenceId());
            newTask.setReferenceType(item.getReferenceType());
            newTask.setTask(item.getTask());
            newTask.setAssigneeId(item.getAssigneeId());
            newTask.setPriority(item.getPriority());
            newTask.setTaskDeadlineTime(item.getTaskDeadlineTime());
            newTask.setStatus(TaskStatus.ASSIGNED);
            newTask.setDescription("New task created.");
            createdTasks.add(taskRepository.save(newTask));
        }
        return taskMapper.modelListToDtoList(createdTasks);
    }

    @Override
    public List<TaskManagementDto> updateTasks(UpdateTaskRequest updateRequest) {
        List<TaskManagement> updatedTasks = new ArrayList<>();
        for (UpdateTaskRequest.RequestItem item : updateRequest.getRequests()) {
            TaskManagement task = taskRepository.findById(item.getTaskId())
                    .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + item.getTaskId()));
            
            if (item.getTaskStatus() != null) {
                TaskStatus oldStatus = task.getStatus();
                task.setStatus(item.getTaskStatus());
                if(oldStatus != item.getTaskStatus()){
                     taskRepository.saveActivity(createActivity(task.getId(), TaskActivityEventType.STATUS_CHANGED, "Status changed from " + oldStatus + " to " + item.getTaskStatus(), task.getAssigneeId()));
                }
            }
            if (item.getDescription() != null) {
                task.setDescription(item.getDescription());
            }
            updatedTasks.add(taskRepository.save(task));
        }
        return taskMapper.modelListToDtoList(updatedTasks);
    }
    
    @Override
    public TaskManagementDto updateTaskPriority(UpdateTaskPriorityRequest request) {
        TaskManagement task = taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + request.getTaskId()));
        
        Priority oldPriority = task.getPriority();
        if (oldPriority != request.getPriority()) {
            task.setPriority(request.getPriority());
            taskRepository.save(task);
            taskRepository.saveActivity(createActivity(task.getId(), TaskActivityEventType.PRIORITY_CHANGED, "Priority changed from " + oldPriority + " to " + request.getPriority(), null));
        }
        return taskMapper.modelToDto(task);
    }

    @Override
    public List<TaskManagementDto> findTasksByPriority(Priority priority) {
        List<TaskManagement> tasks = taskRepository.findByPriority(priority);
        return taskMapper.modelListToDtoList(tasks);
    }
    
    @Override
    public TaskActivityDto addComment(AddCommentRequest request) {
        taskRepository.findById(request.getTaskId())
                .orElseThrow(() -> new ResourceNotFoundException("Task not found with id: " + request.getTaskId()));
        
        TaskActivity newComment = createActivity(request.getTaskId(), TaskActivityEventType.COMMENT_ADDED, request.getComment(), request.getUserId());
        return taskMapper.activityToDto(taskRepository.saveActivity(newComment));
    }

    @Override
    public String assignByReference(AssignByReferenceRequest request) {
        List<Task> applicableTasks = Task.getTasksByReferenceType(request.getReferenceType());
        List<TaskManagement> existingTasks = taskRepository.findByReferenceIdAndReferenceType(request.getReferenceId(), request.getReferenceType());

        for (Task taskType : applicableTasks) {
            List<TaskManagement> tasksOfType = existingTasks.stream()
                    .filter(t -> t.getTask() == taskType && t.getStatus() != TaskStatus.COMPLETED)
                    .collect(Collectors.toList());

            if (!tasksOfType.isEmpty()) {
                // Bug fix: Cancel all but one and update the remaining one
                TaskManagement taskToUpdate = tasksOfType.get(0);
                Long oldAssignee = taskToUpdate.getAssigneeId();
                if (!oldAssignee.equals(request.getAssigneeId())) {
                    taskToUpdate.setAssigneeId(request.getAssigneeId());
                    taskRepository.save(taskToUpdate);
                    taskRepository.saveActivity(createActivity(taskToUpdate.getId(), TaskActivityEventType.ASSIGNEE_CHANGED, "Assignee changed from " + oldAssignee + " to " + request.getAssigneeId(), request.getAssigneeId()));
                }

                tasksOfType.stream()
                        .skip(1)
                        .forEach(taskToCancel -> {
                            taskToCancel.setStatus(TaskStatus.CANCELLED);
                            taskRepository.save(taskToCancel);
                            taskRepository.saveActivity(createActivity(taskToCancel.getId(), TaskActivityEventType.STATUS_CHANGED, "Task cancelled due to reassignment.", request.getAssigneeId()));
                        });
            } else {
                // Create a new task if none exist
                TaskManagement newTask = new TaskManagement();
                newTask.setReferenceId(request.getReferenceId());
                newTask.setReferenceType(request.getReferenceType());
                newTask.setTask(taskType);
                newTask.setAssigneeId(request.getAssigneeId());
                newTask.setStatus(TaskStatus.ASSIGNED);
                taskRepository.save(newTask);
            }
        }
        return "Tasks assigned successfully for reference " + request.getReferenceId();
    }

    @Override
    public List<TaskManagementDto> fetchTasksByDate(TaskFetchByDateRequest request) {
        List<TaskManagement> tasks = taskRepository.findByAssigneeIdIn(request.getAssigneeIds());

        // Fix Bug #2 and implement Feature 1's "smart" logic
        List<TaskManagement> filteredTasks = tasks.stream()
                .filter(task ->
                        task.getStatus() != TaskStatus.CANCELLED &&
                        (
                            (task.getTaskDeadlineTime() >= request.getStartDate() && task.getTaskDeadlineTime() <= request.getEndDate()) ||
                            (task.getTaskDeadlineTime() > request.getEndDate() && (task.getStatus() == TaskStatus.ASSIGNED || task.getStatus() == TaskStatus.STARTED))
                        )
                )
                .collect(Collectors.toList());

        return taskMapper.modelListToDtoList(filteredTasks);
    }
    
    private TaskActivity createActivity(Long taskId, TaskActivityEventType eventType, String message, Long userId) {
        TaskActivity activity = new TaskActivity();
        activity.setTaskId(taskId);
        activity.setTimestamp(System.currentTimeMillis());
        activity.setEventType(eventType);
        activity.setMessage(message);
        activity.setUserId(userId);
        return activity;
    }
}