package net.mbope.taskmanager.task.internal.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.mbope.taskmanager.task.TaskNotFoundException;
import net.mbope.taskmanager.task.TaskPage;
import net.mbope.taskmanager.task.TaskQuery;
import net.mbope.taskmanager.task.internal.application.port.TaskStore;
import net.mbope.taskmanager.task.internal.domain.Task;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaTaskStore implements TaskStore {
    private final EntityManager entities;

    JpaTaskStore(EntityManager entities) {
        this.entities = entities;
    }

    public Task add(Task task) {
        var entity = new TaskEntity(task);
        entities.persist(entity);
        entities.flush();
        return entity.toDomain();
    }

    public Optional<Task> findById(UUID taskId) {
        return Optional.ofNullable(entities.find(TaskEntity.class, taskId)).map(TaskEntity::toDomain);
    }

    public Optional<Task> findAssignedById(UUID taskId, UUID assigneeId) {
        return entities.createQuery("select t from TaskEntity t where t.id = :id and t.assigneeId = :assignee",
                        TaskEntity.class)
                .setParameter("id", taskId).setParameter("assignee", Objects.requireNonNull(assigneeId))
                .getResultStream().findFirst().map(TaskEntity::toDomain);
    }

    public void save(Task task) {
        required(task.id()).apply(task);
    }

    public void delete(Task task) {
        entities.remove(required(task.id()));
    }

    public TaskPage list(TaskQuery query, UUID assigneeId) {
        var builder = entities.getCriteriaBuilder();
        var count = builder.createQuery(Long.class);
        var counted = count.from(TaskEntity.class);
        count.select(builder.count(counted)).where(predicates(builder, counted, query, assigneeId));
        long total = entities.createQuery(count).getSingleResult();
        int pages = Math.toIntExact(total / query.size() + (total % query.size() == 0 ? 0 : 1));
        long offset = (long) query.page() * query.size();
        if (offset >= total) {
            return new TaskPage(List.of(), query.page(), query.size(), total, pages);
        }
        var selection = builder.createQuery(TaskEntity.class);
        var selected = selection.from(TaskEntity.class);
        selection.select(selected).where(predicates(builder, selected, query, assigneeId))
                .orderBy(builder.desc(selected.get("createdAt")), builder.asc(selected.get("id")));
        var items = entities.createQuery(selection).setFirstResult(Math.toIntExact(offset))
                .setMaxResults(query.size()).getResultList().stream().map(TaskEntity::view).toList();
        return new TaskPage(items, query.page(), query.size(), total, pages);
    }

    private Predicate[] predicates(CriteriaBuilder builder, Root<TaskEntity> root, TaskQuery query, UUID assignee) {
        var filters = new ArrayList<Predicate>();
        if (assignee != null) {
            filters.add(builder.equal(root.get("assigneeId"), assignee));
        }
        if (query.status() != null) {
            filters.add(builder.equal(root.get("status"), query.status()));
        }
        if (query.q() != null) {
            String literal = query.q().toLowerCase(Locale.ROOT).replace("!", "!!")
                    .replace("%", "!%").replace("_", "!_");
            filters.add(builder.like(builder.lower(root.get("title")), "%" + literal + "%", '!'));
        }
        return filters.toArray(Predicate[]::new);
    }

    private TaskEntity required(UUID id) {
        var entity = entities.find(TaskEntity.class, id);
        if (entity == null) {
            throw new TaskNotFoundException();
        }
        return entity;
    }
}
