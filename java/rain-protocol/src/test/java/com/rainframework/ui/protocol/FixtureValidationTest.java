package com.rainframework.ui.protocol;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Parametrized fixture-driven test for contract validation.
 * Loads all valid fixtures from schema/fixtures/valid/ and verifies parsing+validation succeeds.
 * Loads all invalid fixtures from schema/fixtures/invalid/ with paired .error.json files
 * and verifies they fail with the expected error code+path.
 *
 * This test is RED/FAILING in Phase A — ContractParser and ContractValidator don't exist yet.
 * Specification for M1 Phase B implementation.
 */
public class FixtureValidationTest {

  private static final ObjectMapper mapper = new ObjectMapper();
  private static List<Path> validFixtures;
  private static List<Path> invalidFixtures;

  @BeforeAll
  static void loadFixtures() throws IOException {
    String projectRoot = System.getProperty("user.dir");
    Path fixturesDir = Paths.get(projectRoot, "schema", "fixtures");

    validFixtures = new ArrayList<>();
    Path validDir = fixturesDir.resolve("valid");
    if (Files.exists(validDir)) {
      try (var stream = Files.list(validDir)) {
        validFixtures = stream
            .filter(p -> p.toString().endsWith(".json"))
            .collect(Collectors.toList());
      }
    }

    invalidFixtures = new ArrayList<>();
    Path invalidDir = fixturesDir.resolve("invalid");
    if (Files.exists(invalidDir)) {
      try (var stream = Files.list(invalidDir)) {
        invalidFixtures = stream
            .filter(p -> p.toString().endsWith(".json") && !p.toString().endsWith(".error.json"))
            .collect(Collectors.toList());
      }
    }
  }

  @TestFactory
  @DisplayName("Valid fixtures should parse and validate successfully")
  Iterable<DynamicTest> testValidFixtures() {
    return validFixtures.stream()
        .map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
          String content = Files.readString(path, StandardCharsets.UTF_8);
          ContractParser parser = new ContractParser();
          Contract contract = parser.parse(content);
          assertNotNull(contract);

          ContractValidator validator = new ContractValidator();
          ValidationResult result = validator.validate(contract);
          assertTrue(result.isValid(), "Fixture " + path.getFileName() + " should be valid");
        }))
        .collect(Collectors.toList());
  }

  @TestFactory
  @DisplayName("Invalid fixtures should fail validation with expected error code")
  Iterable<DynamicTest> testInvalidFixtures() {
    return invalidFixtures.stream()
        .map(path -> DynamicTest.dynamicTest(path.getFileName().toString(), () -> {
          Path errorPath = path.resolveSibling(
              path.getFileName().toString().replace(".json", ".error.json"));

          if (!Files.exists(errorPath)) {
            fail("Missing .error.json for fixture: " + path.getFileName());
          }

          String content = Files.readString(path, StandardCharsets.UTF_8);
          String expectedStr = Files.readString(errorPath, StandardCharsets.UTF_8);
          @SuppressWarnings("unchecked")
          var expected = (java.util.Map<String, Object>) mapper.readValue(expectedStr, java.util.Map.class);

          String expectedCode = (String) expected.get("code");
          String expectedPath = (String) expected.get("path");

          ContractParser parser = new ContractParser();
          try {
            Contract contract = parser.parse(content);
            ContractValidator validator = new ContractValidator();
            ValidationResult result = validator.validate(contract);
            assertFalse(result.isValid(), "Fixture should be invalid: " + path.getFileName());
            assertEquals(expectedCode, result.getError().getCode().name());
            assertEquals(expectedPath, result.getError().getPath());
          } catch (ParseException e) {
            assertEquals(expectedCode, e.getErrorCode().name());
            assertEquals(expectedPath, e.getPath());
          }
        }))
        .collect(Collectors.toList());
  }
}
