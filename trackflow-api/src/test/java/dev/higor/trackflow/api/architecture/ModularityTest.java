package dev.higor.trackflow.api.architecture;

import static com.tngtech.archunit.core.domain.properties.CanBeAnnotated.Predicates.annotatedWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import dev.higor.trackflow.api.TrackflowApiApplication;
import jakarta.persistence.Entity;
import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.web.bind.annotation.RestController;

/**
 * Turns the conventions of the code style skill into build failures. When a rule fails, fix the
 * code; changing a rule needs a decision recorded in the change's design.md.
 */
class ModularityTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(TrackflowApiApplication.class);

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("dev.higor.trackflow.api");

    /**
     * Rules about controllers and injection points may match nothing while a module has none, so
     * they allow an empty match. This guard makes sure that is never caused by a broken import.
     */
    @Test
    void importsProductionClasses() {
        assertThat(PRODUCTION_CLASSES.contain(TrackflowApiApplication.class)).isTrue();
    }

    @Test
    void modulesHaveNoCyclesAndOnlyUsePublicApis() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        new Documenter(MODULES, Documenter.Options.defaults().withOutputFolder("../docs/modules"))
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }

    @Test
    void injectsThroughConstructorsOnly() {
        NO_CLASSES_SHOULD_USE_FIELD_INJECTION.allowEmptyShould(true).check(PRODUCTION_CLASSES);
    }

    @Test
    void controllersArePackagePrivateAndLiveInWebPackages() {
        classes()
                .that()
                .areAnnotatedWith(RestController.class)
                .should()
                .resideInAPackage("..web..")
                .andShould()
                .notBePublic()
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void controllersNeverReturnEntities() {
        methods()
                .that()
                .areDeclaredInClassesThat()
                .areAnnotatedWith(RestController.class)
                .should()
                .notHaveRawReturnType(annotatedWith(Entity.class))
                .because("the HTTP contract is made of *Response records, not JPA entities")
                .allowEmptyShould(true)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void keepsTheAwsSdkInsideTheStorageAdapter() {
        noClasses()
                .that()
                .resideOutsideOfPackage("..storage.adapter..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("software.amazon..")
                .because("the ObjectStorage port must not leak S3 types to other modules")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void usesJavaTimeInsteadOfLegacyDates() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .belongToAnyOf(Date.class, Calendar.class)
                .check(PRODUCTION_CLASSES);
    }
}
