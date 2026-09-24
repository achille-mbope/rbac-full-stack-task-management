package net.mbope.taskmanager;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

import static org.assertj.core.api.Assertions.assertThat;

class ModularityTests {
    @Test
    void verifiesClosedModuleBoundariesAndGeneratesDocumentation() {
        ApplicationModules modules = ApplicationModules.of(TaskManagerApplication.class);
        assertThat(modules.getModuleByName("user")).isPresent();
        modules.verify();
        new Documenter(modules).writeDocumentation();
    }
}
