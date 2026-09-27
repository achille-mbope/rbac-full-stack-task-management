CREATE TABLE tasks (
    id UUID PRIMARY KEY,
    assignee_id UUID NOT NULL REFERENCES user_accounts(id),
    created_by_id UUID NOT NULL REFERENCES user_accounts(id),
    title TEXT NOT NULL,
    description TEXT,
    status VARCHAR(20) NOT NULL,
    due_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_tasks_title_length CHECK (char_length(title) BETWEEN 1 AND 200),
    CONSTRAINT ck_tasks_description_length CHECK (char_length(description) <= 10000),
    CONSTRAINT ck_tasks_status CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE'))
);

CREATE INDEX ix_tasks_assignee_created_id ON tasks (assignee_id, created_at DESC, id ASC);
CREATE INDEX ix_tasks_created_id ON tasks (created_at DESC, id ASC);

-- No task foreign key: audit history must survive permanent task deletion.
CREATE TABLE task_audit (
    id UUID PRIMARY KEY,
    actor_id UUID NOT NULL,
    task_id UUID NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    action VARCHAR(20) NOT NULL,
    changes_json TEXT NOT NULL,
    CONSTRAINT ck_task_audit_action CHECK (action IN ('UPDATE', 'DELETE'))
);

CREATE INDEX ix_task_audit_task_time ON task_audit (task_id, occurred_at, id);
