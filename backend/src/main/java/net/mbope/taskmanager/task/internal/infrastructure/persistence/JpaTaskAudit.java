package net.mbope.taskmanager.task.internal.infrastructure.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import net.mbope.taskmanager.task.internal.application.port.TaskAudit;
import net.mbope.taskmanager.task.internal.application.port.TaskAuditEntry;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(propagation = Propagation.MANDATORY)
class JpaTaskAudit implements TaskAudit {
    private final EntityManager entities;
    private final ObjectMapper json;

    JpaTaskAudit(EntityManager entities, ObjectMapper json) {
        this.entities = entities;
        this.json = json;
    }

    public void append(TaskAuditEntry entry) {
        try {
            entities.persist(new TaskAuditEntity(entry, json.writeValueAsString(entry.changes())));
            // Flush both task and audit writes before leaving the shared transaction.
            entities.flush();
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException("Cannot serialize task audit changes.", failure);
        }
    }
}
