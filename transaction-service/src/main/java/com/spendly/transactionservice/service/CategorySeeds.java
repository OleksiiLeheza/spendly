package com.spendly.transactionservice.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class CategorySeeds {

    static final String DEFAULT_LANGUAGE = "en";
    private static final Set<String> LANGUAGES = Set.of("en", "uk");

    private final ObjectMapper json;
    private final Map<String, List<CategorySeed>> cache = new ConcurrentHashMap<>();

    public CategorySeeds(ObjectMapper json) {
        this.json = json;
    }

    public String languageFor(Locale locale) {
        String language = locale.getLanguage().toLowerCase(Locale.ROOT);
        return LANGUAGES.contains(language) ? language : DEFAULT_LANGUAGE;
    }

    public List<CategorySeed> forLanguage(String language) {
        return cache.computeIfAbsent(language, this::load);
    }

    private List<CategorySeed> load(String language) {
        ClassPathResource resource = new ClassPathResource("categories/defaults." + language + ".json");
        try (InputStream in = resource.getInputStream()) {
            return List.copyOf(
                    json.readValue(in, json.getTypeFactory().constructCollectionType(List.class, CategorySeed.class)));
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read category seeds for " + language, ex);
        }
    }
}
