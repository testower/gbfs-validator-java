/*
 *
 *
 *  * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 *  * the European Commission - subsequent versions of the EUPL (the "Licence");
 *  * You may not use this work except in compliance with the Licence.
 *  * You may obtain a copy of the Licence at:
 *  *
 *  *   https://joinup.ec.europa.eu/software/page/eupl
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the Licence is distributed on an "AS IS" basis,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the Licence for the specific language governing permissions and
 *  * limitations under the Licence.
 *
 */

package org.entur.gbfs.validation.validator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.util.List;
import org.entur.gbfs.validation.model.FileValidationError;
import org.entur.gbfs.validation.model.FileValidationResult;
import org.entur.gbfs.validation.validator.GbfsJsonValidator;
import org.everit.json.schema.Schema;
import org.everit.json.schema.ValidationException;
import org.junit.jupiter.api.Test;

class FileValidatorTest {

  @Test
  void testMapToValidationErrorsWithNullSchemaLocation() {
    // Create a mock ValidationException with null schemaLocation
    ValidationException mockException = mock(ValidationException.class);
    when(mockException.getSchemaLocation()).thenReturn(null);
    when(mockException.getViolatedSchema()).thenReturn(null);
    when(mockException.getPointerToViolation()).thenReturn("#/data/bikes/0");
    when(mockException.getMessage()).thenReturn("Test error message");
    when(mockException.getKeyword()).thenReturn("required");
    when(mockException.getCausingExceptions()).thenReturn(List.of());

    // Create a FileValidator instance and test the mapping
    FileValidator fileValidator = FileValidator.getFileValidator("2.3");
    List<FileValidationError> errors = fileValidator.mapToValidationErrors(
      mockException
    );

    // Verify that schemaPath is never null, should default to "#"
    assertNotNull(errors, "Errors list should not be null");
    assertEquals(1, errors.size(), "Should have exactly one error");

    FileValidationError error = errors.get(0);
    assertNotNull(error.schemaPath(), "schemaPath should not be null");
    assertEquals(
      "#",
      error.schemaPath(),
      "schemaPath should default to '#' when no location information is available"
    );
    assertEquals(
      "#/data/bikes/0",
      error.violationPath(),
      "violationPath should be preserved"
    );
    assertEquals(
      "Test error message",
      error.message(),
      "message should be preserved"
    );
    assertEquals("required", error.keyword(), "keyword should be preserved");
  }

  @Test
  void testMapToValidationErrorsWithValidSchemaLocation() {
    // Create a mock ValidationException with a valid schemaLocation
    ValidationException mockException = mock(ValidationException.class);
    when(mockException.getSchemaLocation())
      .thenReturn("#/properties/data/properties/bikes/items");
    when(mockException.getPointerToViolation()).thenReturn("#/data/bikes/0");
    when(mockException.getMessage()).thenReturn("Test error message");
    when(mockException.getKeyword()).thenReturn("type");
    when(mockException.getCausingExceptions()).thenReturn(List.of());

    // Create a FileValidator instance and test the mapping
    FileValidator fileValidator = FileValidator.getFileValidator("2.3");
    List<FileValidationError> errors = fileValidator.mapToValidationErrors(
      mockException
    );

    // Verify that schemaPath is preserved when not null
    assertNotNull(errors, "Errors list should not be null");
    assertEquals(1, errors.size(), "Should have exactly one error");

    FileValidationError error = errors.get(0);
    assertNotNull(error.schemaPath(), "schemaPath should not be null");
    assertEquals(
      "#/properties/data/properties/bikes/items",
      error.schemaPath(),
      "schemaPath should be preserved when ValidationException.getSchemaLocation() is not null"
    );
    assertEquals(
      "#/data/bikes/0",
      error.violationPath(),
      "violationPath should be preserved"
    );
    assertEquals(
      "Test error message",
      error.message(),
      "message should be preserved"
    );
    assertEquals("type", error.keyword(), "keyword should be preserved");
  }

  @Test
  void testMapToValidationErrorsFallbackToViolatedSchemaLocation() {
    // Create a mock Schema with a schemaLocation
    Schema mockSchema = mock(Schema.class);
    when(mockSchema.getSchemaLocation())
      .thenReturn("#/properties/data/oneOf/0");

    // Create a mock ValidationException with null schemaLocation but valid violated schema
    ValidationException mockException = mock(ValidationException.class);
    when(mockException.getSchemaLocation()).thenReturn(null);
    when(mockException.getViolatedSchema()).thenReturn(mockSchema);
    when(mockException.getPointerToViolation()).thenReturn("#/data");
    when(mockException.getMessage()).thenReturn("no subschema matched");
    when(mockException.getKeyword()).thenReturn("oneOf");
    when(mockException.getCausingExceptions()).thenReturn(List.of());

    // Create a FileValidator instance and test the mapping
    FileValidator fileValidator = FileValidator.getFileValidator("2.3");
    List<FileValidationError> errors = fileValidator.mapToValidationErrors(
      mockException
    );

    // Verify that schemaPath falls back to violated schema's location
    assertNotNull(errors, "Errors list should not be null");
    assertEquals(1, errors.size(), "Should have exactly one error");

    FileValidationError error = errors.get(0);
    assertNotNull(error.schemaPath(), "schemaPath should not be null");
    assertEquals(
      "#/properties/data/oneOf/0",
      error.schemaPath(),
      "schemaPath should fall back to violated schema's location when exception's location is null"
    );
    assertEquals(
      "#/data",
      error.violationPath(),
      "violationPath should be preserved"
    );
    assertEquals(
      "no subschema matched",
      error.message(),
      "message should be preserved"
    );
    assertEquals("oneOf", error.keyword(), "keyword should be preserved");
  }

  @Test
  void testRealValidationErrorsHaveSchemaPath() {
    // Test with actual validation to see what schemaLocation values we get
    GbfsJsonValidator validator = new GbfsJsonValidator();

    InputStream freeBikeStatus = getFixture(
      "fixtures/v2.3/free_bike_status_with_error.json"
    );
    FileValidationResult result = validator.validateFile(
      "free_bike_status",
      freeBikeStatus
    );

    assertNotNull(result, "Validation result should not be null");
    assertTrue(result.errorsCount() > 0, "Should have validation errors");

    // Check that all errors have non-null schemaPath
    for (FileValidationError error : result.errors()) {
      assertNotNull(
        error.schemaPath(),
        String.format(
          "schemaPath should never be null. Error: keyword=%s, message=%s, violationPath=%s",
          error.keyword(),
          error.message(),
          error.violationPath()
        )
      );

      // Log the error details for investigation
      System.out.println(
        String.format(
          "Validation Error - keyword: %s, schemaPath: %s, violationPath: %s, message: %s",
          error.keyword(),
          error.schemaPath(),
          error.violationPath(),
          error.message()
        )
      );
    }
  }

  private InputStream getFixture(String name) {
    return getClass().getClassLoader().getResourceAsStream(name);
  }
}
