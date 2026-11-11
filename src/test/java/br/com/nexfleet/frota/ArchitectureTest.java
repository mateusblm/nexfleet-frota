package br.com.nexfleet.frota;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

class ArchitectureTest {
  private final com.tngtech.archunit.core.domain.JavaClasses classes =
      new ClassFileImporter()
          .withImportOption(new ImportOption.DoNotIncludeTests())
          .importPackages("br.com.nexfleet.frota");

  @Test
  void naoImportaInternosDeOutrosServicos() {
    noClasses()
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "br.com.nexfleet.identidade..",
            "br.com.nexfleet.operacoes..",
            "br.com.nexfleet.indicadores..",
            "br.com.nexfleet.bff..",
            "br.com.nexfleet.gateway..")
        .check(classes);
  }

  @Test
  void aplicacaoNaoDependeDeFrameworkOuAdaptadores() {
    noClasses()
        .that()
        .resideInAPackage("..application..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..", "jakarta.persistence..", "..infrastructure..", "..api..")
        .check(classes);
  }

  @Test
  void apiNaoAcessaPersistenciaDiretamente() {
    noClasses()
        .that()
        .resideInAPackage("..api..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "..infrastructure..", "org.springframework.jdbc..", "jakarta.persistence..")
        .check(classes);
  }

  @Test
  void dominioNaoDependeDeFrameworkOuCamadasExternas() {
    noClasses()
        .that()
        .resideInAPackage("..domain..")
        .should()
        .dependOnClassesThat()
        .resideInAnyPackage(
            "org.springframework..", "jakarta..", "..application..", "..infrastructure..", "..api..")
        .check(classes);
  }
}
