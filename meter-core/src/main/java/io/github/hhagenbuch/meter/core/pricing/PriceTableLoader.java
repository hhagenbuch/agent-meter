package io.github.hhagenbuch.meter.core.pricing;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads a {@link PriceTable} from YAML. Keys are snake_case (matching the vendor-facing
 * shape and the DESIGN); records bind by component name. The price table is data, not
 * code — bundled in the jar and overridable from a file.
 */
public final class PriceTableLoader {

    /** The bundled table shipped on the classpath. */
    public static final String BUNDLED_RESOURCE = "/prices.yaml";

    private static final ObjectMapper YAML = new ObjectMapper(new YAMLFactory())
            .registerModule(new JavaTimeModule())
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    private PriceTableLoader() {
    }

    public static PriceTable bundled() {
        return fromClasspath(BUNDLED_RESOURCE);
    }

    public static PriceTable fromClasspath(String resource) {
        try (InputStream in = PriceTableLoader.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalArgumentException("price table not found on classpath: " + resource);
            }
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PriceTable fromPath(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return read(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static PriceTable read(InputStream in) throws IOException {
        return YAML.readValue(in, PriceTable.class);
    }
}
