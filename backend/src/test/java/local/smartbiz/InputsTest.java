package local.smartbiz;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static local.smartbiz.Inputs.*;

class InputsTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    private final String id = "1234567890abcdef12345678";
    @Test void validatesBoundsAndNestedItems() {
        assertThat(validator.validate(new ProductInput("ABC", "Cafe", id, MAX_PRICE, MAX_STOCK))).isEmpty();
        assertThat(validator.validate(new ProductInput("x", " ", id, 0L, -1L))).hasSize(3);
        assertThat(validator.validate(new SaleInput(List.of(new ItemInput(id, 0L)), 1L))).isNotEmpty();
        assertThat(validator.validate(new SaleInput(List.of(), MAX_MONEY + 1))).hasSize(2);
        assertThat(validator.validate(new SaleInput(java.util.Arrays.asList((ItemInput) null), 1L))).isNotEmpty();
    }
    @Test void maximumCartCannotOverflowLong() {
        long subtotal = Math.multiplyExact(MAX_PRICE, MAX_STOCK);
        assertThat(Math.multiplyExact(subtotal, 100)).isLessThan(Long.MAX_VALUE);
    }
    @Test void normalizesNames() {
        assertThat(normalized("  Cafe   DEMO  ")).isEqualTo("cafe demo");
    }
}
