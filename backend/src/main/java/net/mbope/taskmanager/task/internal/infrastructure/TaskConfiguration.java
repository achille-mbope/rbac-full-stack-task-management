package net.mbope.taskmanager.task.internal.infrastructure;

import java.time.Clock;
import net.mbope.taskmanager.task.PersonalTasks;
import net.mbope.taskmanager.task.TaskAdministration;
import net.mbope.taskmanager.task.internal.application.PersonalTaskService;
import net.mbope.taskmanager.task.internal.application.TaskAdministrationService;
import net.mbope.taskmanager.task.internal.application.port.CurrentTaskActor;
import net.mbope.taskmanager.task.internal.application.port.TaskAudit;
import net.mbope.taskmanager.task.internal.application.port.TaskStore;
import net.mbope.taskmanager.user.AccountLookup;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class TaskConfiguration {
    @Bean
    PersonalTasks personalTasks(TaskStore tasks, CurrentTaskActor actor, Clock clock) {
        return new PersonalTaskService(tasks, actor, clock);
    }

    @Bean
    TaskAdministration taskAdministration(TaskStore tasks, CurrentTaskActor actor, AccountLookup accounts,
                                          TaskAudit audit, Clock clock) {
        return new TaskAdministrationService(tasks, actor, accounts, audit, clock);
    }
}
