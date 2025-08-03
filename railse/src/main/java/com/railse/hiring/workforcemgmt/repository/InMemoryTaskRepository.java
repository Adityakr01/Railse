package com.railse.hiring.workforcemgmt.repository;

import com.railse.hiring.workforcemgmt.common.model.enums.ReferenceType;
import com.railse.hiring.workforcemgmt.model.TaskActivity;
import com.railse.hiring.workforcemgmt.model.TaskManagement;
import com.railse.hiring.workforcemgmt.model.enums.Priority;
import com.railse.hiring.workforcemgmt.model.enums.Task;
import com.railse.hiring.workforcemgmt.model.enums.TaskActivityEventType;
import com.railse.hiring.workforcemgmt.model.enums.TaskStatus;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Repository
public class InMemoryTaskRepository implements TaskRepository {

    private final Map<Long, TaskManagement> taskStore = new ConcurrentHashMap<>();
    private final Map<Long, List<TaskActivity>> activityStore = new ConcurrentHashMap<>();
    private final AtomicLong taskIdCounter = new AtomicLong(0);
    private final AtomicLong activityIdCounter = new AtomicLong(0);

    public InMemoryTaskRepository() {
        // Seed data
        TaskManagement task1 = createSeedTask(101L, ReferenceType.ORDER, Task.CREATE_INVOICE, 1L, TaskStatus.ASSIGNED, Priority.HIGH);
        createSeedTask(101L, ReferenceType.ORDER, Task.ARRANGE_PICKUP, 1L, TaskStatus.COMPLETED, Priority.HIGH);
        TaskManagement task3 = createSeedTask(102L, ReferenceType.ORDER, Task.CREATE_INVOICE, 2L, TaskStatus.ASSIGNED, Priority.MEDIUM);
        TaskManagement task4 = createSeedTask(201L, ReferenceType.ENTITY, Task.ASSIGN_CUSTOMER_TO_SALES_PERSON, 2L, TaskStatus.ASSIGNED, Priority.LOW);
        TaskManagement task5 = createSeedTask(201L, ReferenceType.ENTITY, Task.ASSIGN_CUSTOMER_TO_SALES_PERSON, 3L, TaskStatus.ASSIGNED, Priority.LOW); // Duplicate for Bug #1
        TaskManagement task6 = createSeedTask(103L, ReferenceType.ORDER, Task.COLLECT_PAYMENT, 1L, TaskStatus.CANCELLED, Priority.MEDIUM); // For Bug #2

        // Seed activities
        createSeedActivity(task1.getId(), TaskActivityEventType.TASK_CREATED, "Task created for ORDER 101", 1001L);
        createSeedActivity(task3.getId(), TaskActivityEventType.TASK_CREATED, "Task created for ORDER 102", 1001L);
        createSeedActivity(task4.getId(), TaskActivityEventType.TASK_CREATED, "Task created for ENTITY 201", 1002L);
        createSeedActivity(task5.getId(), TaskActivityEventType.TASK_CREATED, "Task created for ENTITY 201", 1002L);
    }

    private TaskManagement createSeedTask(Long refId, ReferenceType refType, Task task, Long assigneeId, TaskStatus status, Priority priority) {
        long newId = taskIdCounter.incrementAndGet();
        TaskManagement newTask = new TaskManagement();
        newTask.setId(newId);
        newTask.setReferenceId(refId);
        newTask.setReferenceType(refType);
        newTask.setTask(task);
        newTask.setAssigneeId(assigneeId);
        newTask.setStatus(status);
        newTask.setPriority(priority);
        newTask.setDescription("This is a seed task.");
        newTask.setTaskDeadlineTime(System.currentTimeMillis() + 86400000); // 1 day from now
        taskStore.put(newId, newTask);
        return newTask;
    }

    private TaskActivity createSeedActivity(Long taskId, TaskActivityEventType eventType, String message, Long userId) {
        long newId = activityIdCounter.incrementAndGet();
        TaskActivity newActivity = new TaskActivity();
        newActivity.setId(newId);
        newActivity.setTaskId(taskId);
        newActivity.setTimestamp(System.currentTimeMillis());
        newActivity.setEventType(eventType);
        newActivity.setMessage(message);
        newActivity.setUserId(userId);
        activityStore.computeIfAbsent(taskId, k -> new CopyOnWriteArrayList<>()).add(newActivity);
        return newActivity;
    }

    @Override
    public Optional<TaskManagement> findById(Long id) {
        return Optional.ofNullable(taskStore.get(id));
    }

    @Override
    public TaskManagement save(TaskManagement task) {
        boolean isNew = task.getId() == null;
        if (isNew) {
            task.setId(taskIdCounter.incrementAndGet());
        }
        taskStore.put(task.getId(), task);
        if (isNew) {
            saveActivity(createActivity(task.getId(), TaskActivityEventType.TASK_CREATED, "Task created.", task.getAssigneeId()));
        }
        return task;
    }

    @Override
    public List<TaskManagement> findAll() {
        return List.copyOf(taskStore.values());
    }

    @Override
    public List<TaskManagement> findByReferenceIdAndReferenceType(Long referenceId, ReferenceType referenceType) {
        return taskStore.values().stream()
                .filter(task -> task.getReferenceId().equals(referenceId) && task.getReferenceType().equals(referenceType))
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskManagement> findByAssigneeIdIn(List<Long> assigneeIds) {
        return taskStore.values().stream()
                .filter(task -> assigneeIds.contains(task.getAssigneeId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskManagement> findByPriority(Priority priority) {
        return taskStore.values().stream()
                .filter(task -> task.getPriority() == priority)
                .collect(Collectors.toList());
    }

    @Override
    public List<TaskActivity> findActivitiesByTaskId(Long taskId) {
        return activityStore.getOrDefault(taskId, List.of());
    }

    @Override
    public TaskActivity saveActivity(TaskActivity activity) {
        activity.setId(activityIdCounter.incrementAndGet());
        activityStore.computeIfAbsent(activity.getTaskId(), k -> new CopyOnWriteArrayList<>()).add(activity);
        return activity;
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