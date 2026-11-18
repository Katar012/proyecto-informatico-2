package local.smartbiz;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Locale;

public final class Inputs {
    public static final long MAX_PRICE = 1_000_000_000L;
    public static final long MAX_STOCK = 1_000_000L;
    public static final long MAX_MONEY = 1_000_000_000_000_000L;
    private Inputs() {}
    public record Login(@NotBlank @Email @Size(max=254) String email,
                        @NotBlank @Size(max=72) String password) {}
    public record CategoryInput(@NotBlank @Size(max=100) String name) {}
    public record ProductInput(@NotBlank @Pattern(regexp="[A-Za-z0-9._-]{1,40}") String code,
                               @NotBlank @Size(max=160) String name,
                               @NotBlank @Pattern(regexp="[a-fA-F0-9]{24}") String categoryId,
                               @NotNull @Min(1) @Max(MAX_PRICE) Long price,
                               @NotNull @Min(0) @Max(MAX_STOCK) Long stock) {}
    public record ItemInput(@NotBlank @Pattern(regexp="[a-fA-F0-9]{24}") String productId,
                            @NotNull @Min(1) @Max(MAX_STOCK) Long quantity) {}
    public record SaleInput(@NotEmpty @Size(max=100) List<@NotNull @Valid ItemInput> items,
                            @NotNull @Min(1) @Max(MAX_MONEY) Long tendered) {}
    public static String clean(String value) { return value.strip().replaceAll("\\s+", " "); }
    public static String normalized(String value) { return clean(value).toLowerCase(Locale.ROOT); }
}
